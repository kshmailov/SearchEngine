package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.exceptions.IndexingInterruptedException;
import searchengine.model.*;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.interfaces.PageService;
import searchengine.services.SiteIndexingServiceImpl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;
@Slf4j
public class ParsingLinksUrl extends RecursiveAction {

    private final String url;
    private final SiteEntity site;
    private static final Set<String> BLOCKED_EXTENSIONS;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository indexRepository;
    private final SiteIndexingServiceImpl indexingService;
    private final PageService pageService;


    static {
        BLOCKED_EXTENSIONS = new HashSet<>();
        BLOCKED_EXTENSIONS.add(".pdf");
        BLOCKED_EXTENSIONS.add(".doc");
        BLOCKED_EXTENSIONS.add(".docx");
        BLOCKED_EXTENSIONS.add(".jpg");
        BLOCKED_EXTENSIONS.add(".jpeg");
        BLOCKED_EXTENSIONS.add(".png");
        BLOCKED_EXTENSIONS.add(".zip");
        BLOCKED_EXTENSIONS.add(".rar");
        BLOCKED_EXTENSIONS.add(".exe");
        BLOCKED_EXTENSIONS.add(".tar");
        BLOCKED_EXTENSIONS.add(".gz");
    }

    public ParsingLinksUrl(String url,
                           SiteEntity site,
                           PageRepository pageRepository,
                           SiteRepository siteRepository, SiteIndexingServiceImpl indexingService,
                           LemmaRepository lemmaRepository, SearchIndexRepository indexRepository, PageService pageService) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.indexingService=indexingService;
        this.pageService = pageService;
    }

    @Override
    protected void compute() {
        if (Thread.currentThread().isInterrupted() || indexingService.isStopRequested()) {
            log.warn("Остановка обхода: поток прерван или остановлен вручную. Сайт: {}", site.getUrl());
            throw new IndexingInterruptedException("Индексация остановлена пользователем");
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("▶️ Старт обработки: {}", url);
        List<ParsingLinksUrl> allTask = new ArrayList<>();
        try {
            Connection.Response response = indexingService.safeConnect(url);

            int statusCode = response.statusCode();
            log.info("🔗 Ответ от {}: HTTP {}", url, statusCode);
            Document document = response.parse();
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            String path = indexingService.normalizePath(url);
            log.info("📌 Path после normalize: '{}'", path);
            String html = document.html();
            Optional<PageEntity> result = pageRepository.findByPathAndSiteId(path, site.getId());
            if (result.isPresent()) return;
            PageEntity page = pageService.createOrUpdatePage(site, path, statusCode, html);
            log.info("💾 Page сохранена: siteId={}, path='{}'", site.getId(), path);
            indexingService.saveLemmaAndIndex(page);
            log.info("✅ Леммы и индексы сохранены для path='{}'", path);
            Elements links = document.select("a[href]");
            log.info("🔍 Найдено ссылок на странице {}: {}", url, links.size());
            for (Element link : links) {
                if (Thread.currentThread().isInterrupted() || indexingService.isStopRequested()) {
                    log.warn("Остановка обхода внутри цикла: поток прерван или остановлен вручную. Сайт: {}", site.getUrl());
                    throw new IndexingInterruptedException("Индексация остановлена пользователем");
                }
                String href = link.absUrl("href").split("#")[0].split("\\?")[0];
                boolean isFile = BLOCKED_EXTENSIONS.stream().anyMatch(href::endsWith);
                boolean startWithMainUrl = href.startsWith(site.getUrl());
                boolean isNotPage = !href.contains("#");


                if (startWithMainUrl
                        && isNotPage
                        && !isFile) {
                    log.debug("➡️ Fork новая ссылка: {}", href);
                    ParsingLinksUrl task = new ParsingLinksUrl(href, site, pageRepository, siteRepository,
                            indexingService, lemmaRepository, indexRepository, pageService);
                    task.fork();
                    allTask.add(task);
                }
            }
            invokeAll(allTask);
            log.info("✅ Завершено: {}", url);


        } catch (IOException e) {
            log.error("❌ Ошибка при подключении к {}: {}", url, e.getMessage(), e);
            throw new RuntimeException(e);
        }


    }

}

