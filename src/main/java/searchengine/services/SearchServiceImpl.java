package searchengine.services;

import jakarta.annotation.PostConstruct;
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

import static searchengine.utils.UrlUtils.normalizeBaseUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository indexRepository;
    private final SitesList sites;
    private LemmaFinder lemmaFinder;

    @PostConstruct
    private void init() {
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка инициализации лемматизатора", e);
        }
    }
    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        log.info("🔍 Запущен поиск: query='{}', site='{}', offset={}, limit={}", query, site, offset, limit);

        SearchResponse response = new SearchResponse();

        if (query == null || query.trim().isEmpty()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }

        Pageable pageable = PageRequest.of(offset / limit, limit);

        SearchResult result = (site == null || site.isBlank())
                ? searchAllSites(query, pageable)
                : searchOneSite(query, site, pageable);

        response.setResult(true);
        response.setCount(result.total);
        response.setData(result.data);

        log.info("✅ Поиск завершен. Найдено {} из {} возможных.",
                result.data.size(), result.total);

        return response;
    }

    private record SearchResult(long total, List<SearchDto> data) {}

    private SearchResult searchAllSites(String query, Pageable pageable) {
        log.info("🌐 Поиск по всем сайтам");
        long total = 0;
        List<SearchDto> allData = new ArrayList<>();

        for (Site siteConf : sites.getSites()) {
            SearchResult r = searchOneSite(query, siteConf.getUrl(), pageable);
            total += r.total;
            allData.addAll(r.data);
        }

        return new SearchResult(total, allData);
    }

    private SearchResult searchOneSite(String query, String siteUrl, Pageable pageable) {
        String normalizedUrl = normalizeBaseUrl(siteUrl);
        SiteEntity siteEntity = siteRepository.findByUrl(normalizedUrl);

        if (siteEntity == null) {
            log.warn("⛔ Сайт {} не найден.", normalizedUrl);
            return new SearchResult(0, Collections.emptyList());
        }

        List<LemmaEntity> filteredLemmas = getFrequencyFilteredLemmas(query, siteEntity);
        if (filteredLemmas.isEmpty()) {
            return new SearchResult(0, Collections.emptyList());
        }

        LemmaEntity firstLemma = filteredLemmas.get(0);
        long totalCount = pageRepository.countAllByLemmaId(firstLemma.getId());

        if (totalCount == 0) {
            return new SearchResult(0, Collections.emptyList());
        }

        Page<PageEntity> pageEntities = pageRepository.findAllByLemmaId(firstLemma.getId(), pageable);

        if (pageEntities.isEmpty()) {
            return new SearchResult(totalCount, Collections.emptyList());
        }

        double maxRelevance = pageEntities.getContent().stream()
                .map(p -> indexRepository.absoluteRelevanceByPageId(p.getId()))
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(1.0);

        List<SearchDto> data = new ArrayList<>();
        for (PageEntity page : pageEntities) {
            String content = page.getContent();
            String title = extractTitle(content);
            String snippet = getSnippet(content, filteredLemmas);

            double absRelevance = Optional.ofNullable(indexRepository.absoluteRelevanceByPageId(page.getId()))
                    .orElse(0.0);
            double relativeRelevance = absRelevance / maxRelevance;

            SearchDto searchDto = new SearchDto();
            searchDto.setSite(siteEntity.getUrl());
            searchDto.setSiteName(siteEntity.getName());
            searchDto.setUri(page.getPath());
            searchDto.setTitle(title);
            searchDto.setSnippet(snippet);
            searchDto.setRelevance(relativeRelevance);

            data.add(searchDto);
        }

        data.sort(Comparator.comparing(SearchDto::getRelevance).reversed());

        return new SearchResult(totalCount, data);
    }

    private List<LemmaEntity> getFrequencyFilteredLemmas(String query, SiteEntity site) {
        Double maxPercentLemmaOnPageObj = lemmaRepository.findMaxPercentageLemmaOnPagesBySiteId(site.getId());
        double maxPercentLemmaOnPage = (maxPercentLemmaOnPageObj != null) ? maxPercentLemmaOnPageObj : 0.0;
        double limit = maxPercentLemmaOnPage * 0.75;

        Set<String> lemmas = lemmaFinder.getLemmaSet(query);
        List<LemmaEntity> lemmaEntityList = lemmas.stream()
                .map(l -> lemmaRepository.findBySiteIdAndLemma(site.getId(), l).orElse(null))
                .filter(Objects::nonNull).toList();

        List<LemmaEntity> filtered = lemmaEntityList.stream()
                .filter(l -> lemmaRepository.percentageLemmaOnPagesById(l.getId()) < limit)
                .toList();

        return filtered.isEmpty() ? lemmaEntityList : filtered;
    }

    private String extractTitle(String html) {
        int start = html.indexOf("<title>");
        int end = html.indexOf("</title>");
        String title = (start >= 0 && end > start) ? html.substring(start + 7, end).trim() : "";
        log.debug("🔖 Заголовок: {}", title);
        return title;
    }

    private String getSnippet(String content, List<LemmaEntity> queryLemmas) {
        var contentLemmas = lemmaFinder.getLemmaDto(content);
        Map<String, Integer> snippets = new HashMap<>();

        for (LemmaEntity lemmaEntity : queryLemmas) {
            for (LemmaDto lemmaDto : contentLemmas) {
                if (!lemmaDto.getNormalForm().equals(lemmaEntity.getLemma())) continue;

                String regex = "[\\s*()A-Za-zА-Яа-я-,\\d/]*"
                        + Pattern.quote(lemmaDto.getIncomingForm()) + "[\\s*()A-Za-zА-Яа-я-,\\d/]*";
                Matcher matcher = Pattern.compile(regex).matcher(content);

                while (matcher.find()) {
                    String match = matcher.group();
                    String highlighted = match.replaceAll(
                            Pattern.quote(lemmaDto.getIncomingForm()),
                            "<b>" + lemmaDto.getIncomingForm() + "</b>");
                    snippets.put(highlighted, snippets.getOrDefault(highlighted, 0) + 1);
                }
            }
        }
        return snippets.keySet().stream()
                .max(Comparator.comparing(snippets::get))
                .orElse(content.substring(0, Math.min(200, content.length())));
    }

}
