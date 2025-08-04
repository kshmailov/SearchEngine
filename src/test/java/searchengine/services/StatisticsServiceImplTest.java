package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsServiceImplTest {

    @Mock
    private SiteRepository siteRepository;
    @Mock
    private PageRepository pageRepository;
    @Mock
    private LemmaRepository lemmaRepository;
    @Mock
    private SitesList sitesList;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Возвращает статистику для сайтов, которые есть в базе")
    void getStatistics_existingSites_returnsCorrectStatistics() {
        Site configSite = new Site();
        configSite.setUrl("https://site1.ru");
        configSite.setName("Site1");

        when(sitesList.getSites()).thenReturn(List.of(configSite));

        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setId(1);
        siteEntity.setUrl("https://site1.ru");
        siteEntity.setName("Site1");
        siteEntity.setStatus(Status.INDEXED);
        siteEntity.setStatusTime(LocalDateTime.of(2025, 8, 3, 10, 0));
        siteEntity.setLastError(null);

        when(siteRepository.existsByUrl("https://site1.ru")).thenReturn(true);
        when(siteRepository.findByUrl("https://site1.ru")).thenReturn(siteEntity);
        when(pageRepository.countAllBySiteId(1)).thenReturn(100);
        when(lemmaRepository.countAllBySiteId(1)).thenReturn(200);

        when(siteRepository.findAll()).thenReturn(List.of(siteEntity));
        when(siteRepository.findAll()).thenReturn(List.of(siteEntity));


        PageEntity page1 = new PageEntity();
        PageEntity page2 = new PageEntity();
        when(pageRepository.findAll()).thenReturn(List.of(page1, page2));


        LemmaEntity lemma1 = new LemmaEntity();
        LemmaEntity lemma2 = new LemmaEntity();
        LemmaEntity lemma3 = new LemmaEntity();
        when(lemmaRepository.findAll()).thenReturn(List.of(lemma1, lemma2, lemma3));
        when(siteRepository.findAllByStatus(Status.INDEXING)).thenReturn(Optional.of(List.of(siteEntity)));

        StatisticsResponse response = statisticsService.getStatistics();

        assertTrue(response.isResult());
        assertNotNull(response.getStatistics());

        var total = response.getStatistics().getTotal();
        var detailed = response.getStatistics().getDetailed();

        assertEquals(1, total.getSites());
        assertEquals(2, total.getPages());
        assertEquals(3, total.getLemmas());
        assertTrue(total.isIndexing());

        assertEquals(1, detailed.size());
        DetailedStatisticsItem item = detailed.get(0);
        assertEquals("https://site1.ru", item.getUrl());
        assertEquals("Site1", item.getName());
        assertEquals(Status.INDEXED, item.getStatus());
        assertEquals(100, item.getPages());
        assertEquals(200, item.getLemmas());
        assertNull(item.getError());
        assertEquals(LocalDateTime.of(2025, 8, 3, 10, 0), item.getStatusTime());
    }

    @Test
    @DisplayName("Если сайта нет в базе, возвращаются данные из конфигурации")
    void getStatistics_siteNotInDatabase_usesConfigData() {
        Site configSite = new Site();
        configSite.setUrl("https://site2.ru");
        configSite.setName("Site2");

        when(sitesList.getSites()).thenReturn(List.of(configSite));
        when(siteRepository.existsByUrl("https://site2.ru")).thenReturn(false);

        when(siteRepository.findAll()).thenReturn(List.of());
        when(pageRepository.findAll()).thenReturn(List.of());
        when(lemmaRepository.findAll()).thenReturn(List.of());
        when(siteRepository.findAllByStatus(Status.INDEXING)).thenReturn(Optional.empty());

        StatisticsResponse response = statisticsService.getStatistics();

        assertTrue(response.isResult());
        assertNotNull(response.getStatistics());

        var total = response.getStatistics().getTotal();
        var detailed = response.getStatistics().getDetailed();

        assertEquals(0, total.getSites());
        assertEquals(0, total.getPages());
        assertEquals(0, total.getLemmas());
        assertFalse(total.isIndexing());

        assertEquals(1, detailed.size());
        DetailedStatisticsItem item = detailed.get(0);
        assertEquals("https://site2.ru", item.getUrl());
        assertEquals("Site2", item.getName());
        assertNull(item.getStatus());
        assertEquals(0, item.getPages());
        assertEquals(0, item.getLemmas());
        assertNull(item.getError());
        assertNotNull(item.getStatusTime()); // время текущего вызова
    }
}
