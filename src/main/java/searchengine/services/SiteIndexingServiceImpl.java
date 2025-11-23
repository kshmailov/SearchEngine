package searchengine.services;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.IndexResponse;
import searchengine.exceptions.IndexingInterruptedException;
import searchengine.model.*;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.interfaces.PageService;
import searchengine.services.interfaces.SiteIndexingService;
import searchengine.utils.LemmaFinder;
import searchengine.utils.ParsingLinksUrl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

import static searchengine.utils.UrlUtils.normalizeBaseUrl;

@Slf4j
@Service
public class SiteIndexingServiceImpl implements SiteIndexingService {


    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository indexRepository;
    private final PageService pageService;
    private final SitesList sitesList;
    private final List<Thread> threads = new ArrayList<>();
    @Getter
    private volatile boolean stopRequested = false;

    @Autowired
    public SiteIndexingServiceImpl(PageRepository pageRepository, SiteRepository siteRepository, SitesList sitesList,
                                   LemmaRepository lemmaRepository, SearchIndexRepository indexRepository, PageService pageService) {
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.sitesList = sitesList;
        this.pageService = pageService;
    }

    @Override
    public IndexResponse startFullIndexing() {
        IndexResponse indexResponse = new IndexResponse();

        if (threads.stream().anyMatch(Thread::isAlive)) {
            indexResponse.setResult(false);
            indexResponse.setError("Индексация уже запущена");
            log.warn("Попытка запустить индексацию, но она уже активна");
            return indexResponse;
        }

        log.info("▶️ Запуск полной индексации сайтов");

        stopRequested = false;
        threads.clear();

        List<Site> siteInfos = sitesList.getSites();
        for (Site siteInfo : siteInfos) {
            Thread thread = new Thread(() -> {
                String url = normalizeBaseUrl(siteInfo.getUrl());
                log.info("🔗 Индексация сайта: {}", url);

                siteRepository.deleteByUrl(url);

                SiteEntity site = new SiteEntity();
                site.setName(siteInfo.getName());
                site.setUrl(url);
                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());

                try {
                    parsingSite(url, site);
                    site.setStatus(Status.INDEXED);
                } catch (IndexingInterruptedException e) {
                    site.setStatus(Status.FAILED);
                    site.setLastError(e.getMessage());
                    log.warn("🛑 Индексация остановлена пользователем для сайта {}: {}", url, e.getMessage());
                } catch (Exception e) {
                    site.setStatus(Status.FAILED);
                    site.setLastError("Ошибка при обходе: " + e.getMessage());
                    log.error("❌ Ошибка индексации для {}: {}", url, e.getMessage(), e);
                }

                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            });

            threads.add(thread);
        }

        threads.forEach(Thread::start);

        indexResponse.setResult(true);
        return indexResponse;
    }

    private void parsingSite(String url, SiteEntity site) {
        log.info("🔍 Запускаем ForkJoinPool для сайта: {}", url);
        ParsingLinksUrl parsingLinksUrl = new ParsingLinksUrl(url, site, pageRepository, siteRepository, this,
                lemmaRepository, indexRepository, pageService);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPool.invoke(parsingLinksUrl);
    }

    @Override
    public IndexResponse stopFullIndexing() {
        IndexResponse indexResponse = new IndexResponse();

        if (siteRepository.existsByStatus(Status.INDEXING)) {
            log.info("⏹️ Остановка индексации по запросу");

            stopRequested = true;
            threads.forEach(Thread::interrupt);

            List<SiteEntity> indexingSites = siteRepository.findByStatus(Status.INDEXING);
            for (SiteEntity site : indexingSites) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                log.info("🚫 Индексация сайта остановлена: {}", site.getUrl());
            }

            indexResponse.setResult(true);
        } else {
            log.warn("Попытка остановить индексацию, но активных задач нет");
            indexResponse.setResult(false);
            indexResponse.setError("Индексация не запущена");
        }

        return indexResponse;
    }

    @Override
    public IndexResponse indexPage(String url) throws IOException {
        log.info("🔄 Индексация одной страницы: {}", url);

        ParsedUrl parsed;
        try {
            parsed = parseUrl(url);
        } catch (MalformedURLException e) {
            return errorResponse("Некорректный URL: " + url);
        }

        Site siteInfo = validateSiteFromConfig(parsed.prefix(), url);
        if (siteInfo == null) {
            return errorResponse("Данная страница находится за пределами сайтов, указанных в конфигурации");
        }

        SiteEntity site = getOrCreateSiteEntity(parsed.prefix(), siteInfo);
        removeExistingPageIfExists(parsed.suffix(), site);

        return downloadAndIndexPage(url, parsed.suffix(), site);
    }


    public String normalizePath(String url) {
        try {
            URL fullUrl = new URL(url);
            String path = fullUrl.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            log.debug("Нормализованный путь для {} → '{}'", url, path);
            return path;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Некорректный URL: " + url, e);
        }
    }

    public void saveLemmaAndIndex(PageEntity page) throws IOException {

        log.info("▶️ Сохраняем леммы и индексы для page id={} path='{}'", page.getId(), page.getPath());

        LemmaFinder lemmaFinder = LemmaFinder.getInstance();
        List<SearchIndexEntity> searchIndexEntities = new ArrayList<>();

        int siteId = page.getSite().getId();
        Map<String, Integer> lemmas;

        try {
            lemmas = lemmaFinder.collectLemmas(page.getContent());
        } catch (Exception e) {
            log.error("⚠ Ошибка лемматизации, индексируем без лемм. page={}", page.getId(), e);
            return;
        }

        for (Map.Entry<String, Integer> word : lemmas.entrySet()) {
            String lemmaKey = word.getKey();

            lemmaRepository.upsertLemma(lemmaKey, siteId);

            LemmaEntity lemma = lemmaRepository.findBySiteIdAndLemma(siteId, lemmaKey)
                    .orElseThrow(() -> new IllegalStateException("Lemma не найдена после UPSERT: " + lemmaKey));
            SearchIndexEntity indexEntry = new SearchIndexEntity();
            indexEntry.setPage(page);
            indexEntry.setLemma(lemma);
            indexEntry.setLemmaRank(Float.valueOf(word.getValue()));
            searchIndexEntities.add(indexEntry);
            if (searchIndexEntities.size() >= 5000) {
                indexRepository.saveAll(searchIndexEntities);
                searchIndexEntities.clear();
            }
        }

        if (!searchIndexEntities.isEmpty()) {
            indexRepository.saveAll(searchIndexEntities);
        }

        log.info("✅ Леммы и индексы сохранены для page id={}", page.getId());
    }
    public Connection.Response safeConnect(String url) throws IOException {
        int attempts = 3;
        IOException lastEx = null;

        for (int i = 1; i <= attempts; i++) {
            try {
                return Jsoup.connect(url)
                        .timeout(10_000)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .userAgent("HeliontSearchBot/1.0 (+https://heliont.example.com/bot-info)")
                        .referrer("https://www.google.com")
                        .execute();
            } catch (UnknownHostException e) {
                log.error("❌ DNS не найден: {}", url);
                throw e;
            } catch (IOException e) {
                lastEx = e;
                log.warn("⏳ Попытка {} не удалась для {}: {}", i, url, e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Поток прерван при ретрае", ie);
                }
            }
        }

        throw new IOException("Не удалось подключиться к " + url + " после " + attempts + " попыток", lastEx);
    }
    private record ParsedUrl(String prefix, String suffix) {}
    private ParsedUrl parseUrl(String url) throws MalformedURLException {
        URL parsedUrl = new URL(url);
        String prefix = normalizeBaseUrl(parsedUrl.getProtocol() + "://" + parsedUrl.getHost());
        String suffix = parsedUrl.getPath().isEmpty() ? "/" : parsedUrl.getPath();
        return new ParsedUrl(prefix, suffix);
    }
    private Site validateSiteFromConfig(String prefix, String url) {
        return sitesList.getSites().stream()
                .filter(s -> normalizeBaseUrl(s.getUrl()).equals(prefix))
                .findFirst()
                .orElse(null);
    }
    private SiteEntity getOrCreateSiteEntity(String prefix, Site siteInfo) {
        SiteEntity site = siteRepository.findByUrl(prefix);
        if (site == null) {
            site = new SiteEntity();
            site.setUrl(prefix);
            site.setName(siteInfo.getName());
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            log.info("📌 Новый Site сохранён: {}", site.getUrl());
        }
        return site;
    }
    private void removeExistingPageIfExists(String path, SiteEntity site) {
        pageRepository.findByPathAndSiteId(path, site.getId()).ifPresent(page -> {
            log.info("♻️ Найдена старая страница. Удаляем: {}", path);
            try {
                LemmaFinder lemmaFinder = LemmaFinder.getInstance();
                Set<String> lemmas = lemmaFinder.getLemmaSet(page.getContent());
                lemmas.forEach(l -> lemmaRepository.decrementAllFrequencyBySiteIdAndLemma(site.getId(), l));
            } catch (IOException e) {
                log.error("Ошибка лемматизации при удалении страницы {}", path, e);
            }

            indexRepository.deleteAllByPageId(page.getId());
            pageRepository.delete(page);
        });
    }
    private IndexResponse downloadAndIndexPage(String url, String path, SiteEntity site) throws IOException {
        try {
            Connection.Response response = safeConnect(url);
            Document document = response.parse();
            PageEntity newPage = pageService.createOrUpdatePage(site, path, response.statusCode(), document.html());

            saveLemmaAndIndex(newPage);

            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            return successResponse();
        } catch (IOException e) {
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            log.error("❌ Ошибка при скачивании {}: {}", url, e.getMessage());
            throw e;
        }
    }
    private IndexResponse successResponse() {
        IndexResponse res = new IndexResponse();
        res.setResult(true);
        return res;
    }

    private IndexResponse errorResponse(String message) {
        IndexResponse res = new IndexResponse();
        res.setResult(false);
        res.setError(message);
        return res;
    }










}
