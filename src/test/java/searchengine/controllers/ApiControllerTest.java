package searchengine.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import searchengine.dto.IndexResponse;
import searchengine.dto.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.interfaces.SearchService;
import searchengine.services.interfaces.SiteIndexingService;
import searchengine.services.interfaces.StatisticsService;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class ApiControllerTest {

    private StatisticsService statisticsService;
    private SiteIndexingService siteIndexingService;
    private SearchService searchService;
    private ApiController apiController;

    @BeforeEach
    void setUp() {
        statisticsService = Mockito.mock(StatisticsService.class);
        siteIndexingService = Mockito.mock(SiteIndexingService.class);
        searchService = Mockito.mock(SearchService.class);
        apiController = new ApiController(statisticsService, siteIndexingService, searchService);
    }

    @Test
    @DisplayName("Получение статистики — возвращает объект StatisticsResponse")
    void testStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        when(statisticsService.getStatistics()).thenReturn(response);

        ResponseEntity<StatisticsResponse> result = apiController.statistics();

        assertEquals(response, result.getBody());
    }

    @Test
    @DisplayName("Запуск полного индексирования — возвращает IndexResponse")
    void testStartIndexing() {
        IndexResponse response = new IndexResponse();
        when(siteIndexingService.startFullIndexing()).thenReturn(response);

        ResponseEntity<IndexResponse> result = apiController.startIndexing();

        assertEquals(response, result.getBody());
    }

    @Test
    @DisplayName("Остановка индексирования — возвращает IndexResponse")
    void testStopIndexing() {
        IndexResponse response = new IndexResponse();
        when(siteIndexingService.stopFullIndexing()).thenReturn(response);

        ResponseEntity<IndexResponse> result = apiController.stopIndexing();

        assertEquals(response, result.getBody());
    }

    @Test
    @DisplayName("Индексирование страницы по URL — возвращает IndexResponse")
    void testIndexPage() throws IOException {
        String url = "https://example.com";
        IndexResponse response = new IndexResponse();
        when(siteIndexingService.indexPage(url)).thenReturn(response);

        ResponseEntity<IndexResponse> result = apiController.indexPage(url);

        assertEquals(response, result.getBody());
    }

    @Test
    @DisplayName("Поиск по запросу — возвращает SearchResponse")
    void testSearch() throws IOException {
        String query = "test query";
        String site = "example.com";
        int offset = 0;
        int limit = 10;
        SearchResponse response = new SearchResponse();
        when(searchService.search(query, site, offset, limit)).thenReturn(response);

        ResponseEntity<SearchResponse> result = apiController.search(query, site, offset, limit);

        assertEquals(response, result.getBody());
    }
}
