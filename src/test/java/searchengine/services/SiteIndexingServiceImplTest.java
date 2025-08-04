package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.IndexResponse;
import searchengine.model.*;
import searchengine.repositories.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteIndexingServiceImplTest {

    @Mock
    private SiteRepository siteRepository;
    @Mock
    private SitesList sitesList;

    @InjectMocks
    private SiteIndexingServiceImpl siteIndexingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(siteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(siteRepository).deleteByUrl(anyString());
    }

    @Test
    @DisplayName("Старт полной индексации: возвращает ошибку, если поток уже запущен")
    void startFullIndexing_ShouldReturnError_WhenThreadAlreadyRunning() throws InterruptedException {
        Site site = new Site();
        site.setUrl("https://example.com");
        site.setName("Example");

        when(sitesList.getSites()).thenReturn(List.of(site));

        IndexResponse firstResponse = siteIndexingService.startFullIndexing();
        assertTrue(firstResponse.isResult());

        IndexResponse secondResponse = siteIndexingService.startFullIndexing();
        assertFalse(secondResponse.isResult());
        assertEquals("Индексация уже запущена", secondResponse.getError());

        siteIndexingService.stopFullIndexing();
    }

    @Test
    @DisplayName("Остановка индексации: возвращает false, если индексация не запущена")
    void stopFullIndexing_ShouldReturnFalse_WhenNoIndexing() {
        when(siteRepository.existsByStatus(Status.INDEXING)).thenReturn(false);

        IndexResponse response = siteIndexingService.stopFullIndexing();

        assertFalse(response.isResult());
        assertEquals("Индексация не запущена", response.getError());
    }

    @Test
    @DisplayName("Нормализация пути URL работает корректно")
    void normalizePath_ShouldNormalizeCorrectly() {
        assertEquals("/", siteIndexingService.normalizePath("https://example.com"));
        assertEquals("/path", siteIndexingService.normalizePath("https://example.com/path/"));
        assertEquals("/path/to/resource", siteIndexingService.normalizePath("https://example.com/path/to/resource"));
    }

    @Test
    @DisplayName("Индексация страницы: возвращает ошибку при некорректном URL")
    void indexPage_ShouldReturnError_ForMalformedUrl() throws IOException {
        IndexResponse response = siteIndexingService.indexPage("malformed-url");
        assertFalse(response.isResult());
        assertTrue(response.getError().startsWith("Некорректный URL"));
    }

    @Test
    @DisplayName("Индексация страницы: возвращает ошибку, если сайт не указан в конфигурации")
    void indexPage_ShouldReturnError_WhenSiteNotInConfig() throws IOException {
        when(sitesList.getSites()).thenReturn(Collections.emptyList());

        IndexResponse response = siteIndexingService.indexPage("https://unknownsite.com/page");

        System.out.println("Error: " + response.getError());  // для отладки

        assertFalse(response.isResult());
        assertNotNull(response.getError());
        assertTrue(response.getError().toLowerCase().contains("находится за пределами сайтов, указанных в конфигурации"));
    }
}
