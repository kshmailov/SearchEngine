package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import searchengine.config.SitesList;
import searchengine.dto.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;


import java.util.List;
import java.util.Optional;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchServiceImplTest {

    @InjectMocks
    private SearchServiceImpl searchService;

    @Mock private SiteRepository siteRepository;
    @Mock private PageRepository pageRepository;
    @Mock private LemmaRepository lemmaRepository;
    @Mock private SearchIndexRepository indexRepository;
    @Mock private SitesList sites;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        searchService = new SearchServiceImpl(
                siteRepository,
                pageRepository,
                lemmaRepository,
                indexRepository,
                sites

        );
    }


    @Test
    @DisplayName("Поиск с пустым запросом возвращает ошибку")
    void search_emptyQuery_returnsError() {
        SearchResponse response = searchService.search("   ", "", 0, 10);

        assertFalse(response.isResult());
        assertEquals("Задан пустой поисковый запрос", response.getError());
    }

    @Test
    @DisplayName("Поиск по несуществующему сайту возвращает пустой результат")
    void search_siteNotFound_returnsEmpty() {
        when(siteRepository.findByUrl("https://unknown.ru")).thenReturn(null);

        SearchResponse response = searchService.search("query", "https://unknown.ru", 0, 10);

        assertTrue(response.isResult());
        assertEquals(0, response.getCount());
    }

    @Test
    @DisplayName("Поиск без найденных лемм возвращает пустой результат")
    void search_noLemmasFound_returnsEmpty() {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setId(1);
        siteEntity.setName("Skillbox");
        when(siteRepository.findByUrl("https://skillbox.ru")).thenReturn(siteEntity);

        when(lemmaRepository.findMaxPercentageLemmaOnPagesBySiteId(1)).thenReturn(0.1);

        when(lemmaRepository.findBySiteIdAndLemma(1, "query")).thenReturn(Optional.empty());

        SearchResponse response = searchService.search("query", "https://skillbox.ru", 0, 10);

        assertTrue(response.isResult());
        assertEquals(0, response.getCount());
    }

    @Test
    @DisplayName("Поиск без страниц после пересечения лемм возвращает пустой результат")
    void search_noPagesAfterIntersection_returnsEmpty() {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setId(1);
        siteEntity.setName("Skillbox");
        when(siteRepository.findByUrl("https://skillbox.ru")).thenReturn(siteEntity);

        LemmaEntity lemma = new LemmaEntity();
        lemma.setId(1);
        lemma.setLemma("query");
        lemma.setFrequency(1);

        when(lemmaRepository.findMaxPercentageLemmaOnPagesBySiteId(1)).thenReturn(0.1);
        when(lemmaRepository.findBySiteIdAndLemma(1, "query")).thenReturn(Optional.of(lemma));
        when(lemmaRepository.percentageLemmaOnPagesById(1)).thenReturn(0.01);

        Page<PageEntity> emptyPage = new PageImpl<>(List.of());
        when(pageRepository.findAllByLemmaId(1, PageRequest.of(0, 10)))
                .thenReturn(emptyPage);

        SearchResponse response = searchService.search("query", "https://skillbox.ru", 0, 10);

        assertTrue(response.isResult());
        assertEquals(0, response.getCount());
    }

}
