package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.IndexResponse;
import searchengine.dto.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.interfaces.SearchService;
import searchengine.services.interfaces.SiteIndexingService;
import searchengine.services.interfaces.StatisticsService;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteIndexingService siteIndexingService;
    private final SearchService searchService;

    @Autowired
    public ApiController(StatisticsService statisticsService, SiteIndexingService siteIndexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.siteIndexingService = siteIndexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
    @GetMapping("/startIndexing")
    public ResponseEntity<IndexResponse> startIndexing() {
        return ResponseEntity.ok(siteIndexingService.startFullIndexing());
    }
    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexResponse> stopIndexing(){
        return ResponseEntity.ok(siteIndexingService.stopFullIndexing());
    }
    @PostMapping("/indexPage")
    public  ResponseEntity<IndexResponse> indexPage(@RequestParam(name = "url", defaultValue = "")String url) throws IOException {
        return ResponseEntity.ok(siteIndexingService.indexPage(url));

    }
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(name = "query", defaultValue = "") String query,
                                                 @RequestParam(name = "site", defaultValue = "") String site,
                                                 @RequestParam(name = "offset", defaultValue = "0") int offset,
                                                 @RequestParam(name = "limit", defaultValue = "10") int limit) throws IOException {
        return ResponseEntity.ok(searchService.search(query, site, offset, limit));
    }
}