package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.interfaces.StatisticsService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        log.info("📊 Запрос статистики сайтов");

        List<Site> siteList = sites.getSites();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        siteList.forEach(site -> {
            DetailedStatisticsItem item = getDetailedStatistic(site);
            detailed.add(item);
            log.info("🔍 Детальная статистика: {} — pages={}, lemmas={}", item.getUrl(), item.getPages(), item.getLemmas());
        });

        TotalStatistics total = getTotalStatistic();

        log.info("✅ Итоговая статистика: sites={}, pages={}, lemmas={}, indexing={}",
                total.getSites(), total.getPages(), total.getLemmas(), total.isIndexing());

        StatisticsResponse statisticsResponse = new StatisticsResponse();
        statisticsResponse.setResult(true);
        statisticsResponse.setStatistics(getStatistic(total, detailed));

        return statisticsResponse;
    }

    private DetailedStatisticsItem getDetailedStatistic(Site siteInfo) {
        boolean isSiteExist = siteRepository.existsByUrl(siteInfo.getUrl());
        SiteEntity site = null;
        int pagesCount = 0;
        int lemmasCount = 0;

        if (isSiteExist) {
            site = siteRepository.findByUrl(siteInfo.getUrl());
            pagesCount = pageRepository.countAllBySiteId(site.getId());
            lemmasCount = lemmaRepository.countAllBySiteId(site.getId());
        }

        DetailedStatisticsItem detailedStatisticsItem = new DetailedStatisticsItem();
        detailedStatisticsItem.setUrl(isSiteExist ? site.getUrl() : siteInfo.getUrl());
        detailedStatisticsItem.setName(isSiteExist ? site.getName() : siteInfo.getName());
        detailedStatisticsItem.setStatus(isSiteExist ? site.getStatus() : null);
        detailedStatisticsItem.setStatusTime(isSiteExist ? site.getStatusTime() : LocalDateTime.now());
        detailedStatisticsItem.setError(isSiteExist ? site.getLastError() : null);
        detailedStatisticsItem.setPages(pagesCount);
        detailedStatisticsItem.setLemmas(lemmasCount);

        if (!isSiteExist) {
            log.warn("⚠️ Сайт {} отсутствует в базе, данные берутся из конфигурации", siteInfo.getUrl());
        }

        return detailedStatisticsItem;
    }

    private TotalStatistics getTotalStatistic() {
        int sitesCount = siteRepository.findAll().size();
        int pagesCount = pageRepository.findAll().size();
        int lemmaCount = lemmaRepository.findAll().size();
        var indexingSites = siteRepository.findAllByStatus(Status.INDEXING);

        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites(sitesCount);
        totalStatistics.setPages(pagesCount);
        totalStatistics.setLemmas(lemmaCount);
        totalStatistics.setIndexing(indexingSites.isPresent());

        return totalStatistics;
    }

    private StatisticsData getStatistic(TotalStatistics total, List<DetailedStatisticsItem> detailed) {
        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setDetailed(detailed);
        statisticsData.setTotal(total);
        return statisticsData;
    }
}
