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
                String url = siteInfo.getUrl().replace("www.", "");
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
        IndexResponse indexResponse = new IndexResponse();

        String prefix;
        String suffix;
        try {
            URL parsedUrl = new URL(url);
            prefix = parsedUrl.getProtocol() + "://" + parsedUrl.getHost();
            suffix = parsedUrl.getPath().isEmpty() ? "/" : parsedUrl.getPath();
        } catch (MalformedURLException e) {
            indexResponse.setError("Некорректный URL: " + url);
            indexResponse.setResult(false);
            log.error("❌ Некорректный URL: {}", url);
            return indexResponse;
        }

        Site siteInfo = sitesList.getSites().stream()
                .filter(s -> s.getUrl().equals(prefix))
                .findFirst()
                .orElse(null);

        if (siteInfo == null) {
            indexResponse.setError("Данная страница находится за пределами сайтов, указанных в конфигурации");
            indexResponse.setResult(false);
            log.warn("⛔ Страница {} вне списка разрешённых сайтов", url);
            return indexResponse;
        }

        SiteEntity site = siteRepository.findByUrl(prefix);
        if (site == null) {
            site = new SiteEntity();
            site.setUrl(siteInfo.getUrl());
            site.setName(siteInfo.getName());
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            log.info("📌 Новый Site сохранён: {}", site.getUrl());
        }

        int siteId = site.getId();
        Optional<PageEntity> optionalPage = pageRepository.findByPathAndSiteId(suffix, siteId);
        if (optionalPage.isPresent()) {
            PageEntity page = optionalPage.get();
            int pageId = page.getId();
            log.info("♻️ Существующая страница будет удалена: path='{}'", suffix);

            LemmaFinder lemmaFinder = LemmaFinder.getInstance();
            Set<String> lemmas = lemmaFinder.getLemmaSet(page.getContent());
            for (String lemma : lemmas) {
                lemmaRepository.decrementAllFrequencyBySiteIdAndLemma(siteId, lemma);
            }

            indexRepository.deleteAllByPageId(pageId);
            pageRepository.delete(page);
            log.info("🗑️ Старая страница удалена: {}", suffix);
        }

        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        try {
            Connection.Response response = Jsoup.connect(url)
                    .timeout(30_000)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .userAgent("HeliontSearchBot/1.0 (+https://heliont.example.com/bot-info)")
                    .referrer("http://www.google.com")
                    .execute();

            int statusCode = response.statusCode();
            log.info("🌐 Ответ от страницы {}: HTTP {}", url, statusCode);

            Document document = response.parse();
            String html = document.html();

            PageEntity newPage = pageService.createOrUpdatePage(site, suffix, statusCode, html);
            log.info("💾 Новая страница сохранена: path='{}'", suffix);

            saveLemmaAndIndex(newPage);
            log.info("✅ Леммы и индексы обновлены: path='{}'", suffix);

            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            indexResponse.setResult(true);
        } catch (IOException e) {
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            log.error("❌ Ошибка при скачивании страницы {}: {}", url, e.getMessage(), e);
            throw e;
        }

        return indexResponse;
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
        Map<String, Integer> lemmas = lemmaFinder.collectLemmas(page.getContent());

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



}
