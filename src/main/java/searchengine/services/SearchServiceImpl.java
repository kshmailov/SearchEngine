package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.LemmaDto;
import searchengine.dto.SearchDto;
import searchengine.dto.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.interfaces.SearchService;
import searchengine.utils.LemmaFinder;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository indexRepository;
    private final SitesList sites;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        log.info("🔍 Запущен поиск: query='{}', site='{}', offset={}, limit={}", query, site, offset, limit);
        SearchResponse response = new SearchResponse();

        if (query == null || query.trim().isEmpty()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            log.warn("⚠️ Поиск остановлен: пустой запрос");
            return response;
        }

        Pageable pageable = PageRequest.of(offset / limit, limit);

        List<SearchDto> data = (site == null || site.isBlank())
                ? searchAllSites(query, pageable)
                : searchOneSite(query, site, pageable);

        response.setResult(true);
        response.setCount(data.size());
        response.setData(data);

        log.info("✅ Поиск завершен. Найдено {} результатов.", data.size());
        return response;
    }

    private List<SearchDto> searchAllSites(String query, Pageable pageable) {
        log.info("🌐 Поиск по всем сайтам");
        List<SearchDto> results = new ArrayList<>();
        for (Site siteConf : sites.getSites()) {
            results.addAll(searchOneSite(query, siteConf.getUrl(), pageable));
        }
        return results;
    }

    private List<SearchDto> searchOneSite(String query, String siteUrl, Pageable pageable) {
        SiteEntity siteEntity = siteRepository.findByUrl(siteUrl);
        if (siteEntity == null) {
            log.warn("⛔ Сайт {} не найден в базе.", siteUrl);
            return Collections.emptyList();
        }

        List<LemmaEntity> filteredLemmas = getFrequencyFilteredLemmas(query, siteEntity);
        if (filteredLemmas.isEmpty()) {
            log.warn("🚫 Нет подходящих лемм после фильтрации");
            return Collections.emptyList();
        }

        Set<PageEntity> pages = new HashSet<>();
        for (LemmaEntity lemma : filteredLemmas) {
            Page<PageEntity> pageEntities = pageRepository.findAllByLemmaId(lemma.getId(), pageable);
            if (pages.isEmpty()) {
                pages.addAll(pageEntities.toList());
            } else {
                pages.retainAll(pageEntities.toList());
            }
        }

        if (pages.isEmpty()) {
            log.warn("⚡ После пересечения страниц не осталось");
            return Collections.emptyList();
        }

        double maxRelevance = pages.stream()
                .map(page -> indexRepository.absoluteRelevanceByPageId(page.getId()))
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(1.0);

        List<SearchDto> data = new ArrayList<>();
        for (PageEntity page : pages) {
            String content = page.getContent();
            String title = extractTitle(content);
            String snippet = getSnippet(content, filteredLemmas);
            double absRelevance = Optional.ofNullable(indexRepository.absoluteRelevanceByPageId(page.getId()))
                    .orElse(0.0);

            double relativeRelevance = absRelevance / maxRelevance;

            SearchDto searchDto = new SearchDto();
            searchDto.setSite(siteUrl);
            searchDto.setSiteName(siteEntity.getName());
            searchDto.setUri(page.getPath());
            searchDto.setTitle(title);
            searchDto.setSnippet(snippet);
            searchDto.setRelevance(relativeRelevance);

            data.add(searchDto);
        }
        data.sort(Comparator.comparing(SearchDto::getRelevance).reversed());

        return data;
    }

    private List<LemmaEntity> getFrequencyFilteredLemmas(String query, SiteEntity site) {
        Double maxPercentLemmaOnPageObj = lemmaRepository.findMaxPercentageLemmaOnPagesBySiteId(site.getId());
        double maxPercentLemmaOnPage = (maxPercentLemmaOnPageObj != null) ? maxPercentLemmaOnPageObj : 0.0;
        double maxFrequencyPercentage = 0.75;
        double frequencyLimit = maxPercentLemmaOnPage * maxFrequencyPercentage;

        Set<String> lemmas = getLemmafinder().getLemmaSet(query);
        List<LemmaEntity> lemmaEntityList = lemmas.stream().map(lemma -> lemmaRepository.findBySiteIdAndLemma(site.getId(), lemma).orElse(null))
                .filter(Objects::nonNull).toList();
        List<LemmaEntity> filterFrequency = lemmaEntityList.stream().filter(lemma -> lemmaRepository.percentageLemmaOnPagesById(lemma.getId()) < frequencyLimit).toList();

        return filterFrequency.isEmpty() ?
                lemmaEntityList.stream().sorted(Comparator.comparing(LemmaEntity::getFrequency)).toList()
                :
                filterFrequency.stream().sorted(Comparator.comparing(LemmaEntity::getFrequency)).toList();
    }

    private String extractTitle(String html) {
        int start = html.indexOf("<title>");
        int end = html.indexOf("</title>");
        String title = (start >= 0 && end > start) ? html.substring(start + 7, end).trim() : "";
        log.debug("🔖 Заголовок: {}", title);
        return title;
    }

    private String getSnippet(String content, List<LemmaEntity> queryLemmas) {
        var contentLemmas = getLemmafinder().getLemmaDto(content);

        Map<String, Integer> snippets = new HashMap<>();
        for (LemmaEntity lemmaEntity : queryLemmas) {
            int countMatches = 0;
            for (LemmaDto lemmaDto : contentLemmas) {
                if (!lemmaDto.getNormalForm().equals(lemmaEntity.getLemma())) continue;
                countMatches++;

                String regex = "[\\s*()A-Za-zА-Яа-я-,\\d/]*"
                        + Pattern.quote(lemmaDto.getIncomingForm()) + "[\\s*()A-Za-zА-Яа-я-,\\d/]*";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(content);

                while (matcher.find()) {
                    String match = matcher.group();
                    String highlighted = match.replaceAll(Pattern.quote(lemmaDto.getIncomingForm()),
                            "<b>" + lemmaDto.getIncomingForm() + "</b>");
                    snippets.put(highlighted, countMatches);
                }
            }
        }

        Optional<Map.Entry<String, Integer>> max = snippets.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        if (max.isEmpty()) {
            log.warn("❗ getSnippet: Не найдено совпадений, возвращаем первые 200 символов");
            return content.substring(0, Math.min(200, content.length()));
        }

        String snippet = max.get().getKey();
        if (snippet.length() > 300) {
            snippet = snippet.substring(0, 300) + "...";
        }
        return snippet;
    }
    private LemmaFinder getLemmafinder() {
        try {
            return LemmaFinder.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

}
