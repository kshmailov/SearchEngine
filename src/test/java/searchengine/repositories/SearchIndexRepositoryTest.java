package searchengine.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class SearchIndexRepositoryTest {

    @Autowired
    private SearchIndexRepository searchIndexRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;


    private SiteEntity testSite;
    private PageEntity testPage;
    private LemmaEntity testLemma1;
    private LemmaEntity testLemma2;

    @BeforeEach
    void setUp() {
        searchIndexRepository.deleteAll();
        pageRepository.deleteAll();
        lemmaRepository.deleteAll();
        siteRepository.deleteAll();

        testSite = new SiteEntity();
        testSite.setName("Test Site");
        testSite.setUrl("https://test.site");
        testSite.setStatus(Status.INDEXING);
        testSite.setStatusTime(LocalDateTime.now());
        siteRepository.save(testSite);

        testPage = new PageEntity();
        testPage.setPath("/test-page");
        testPage.setCode(200);
        testPage.setContent("<html>test</html>");
        testPage.setSite(testSite);
        pageRepository.save(testPage);



        testLemma1 = new LemmaEntity();
        testLemma1.setLemma("lemma1");
        testLemma1.setFrequency(1);
        testLemma1.setSite(testSite);
        lemmaRepository.save(testLemma1);

        testLemma2 = new LemmaEntity();
        testLemma2.setLemma("lemma2");
        testLemma2.setFrequency(1);
        testLemma2.setSite(testSite);
        lemmaRepository.save(testLemma2);


        SearchIndexEntity index1 = new SearchIndexEntity();
        index1.setPage(testPage);
        index1.setLemma(testLemma1);
        index1.setLemmaRank(0.7f);

        SearchIndexEntity index2 = new SearchIndexEntity();
        index2.setPage(testPage);
        index2.setLemma(testLemma2);
        index2.setLemmaRank(0.3f);

        searchIndexRepository.saveAll(List.of(index1, index2));
    }

    @Test
    @DisplayName("Проверка подсчета absoluteRelevanceByPageId")
    void testAbsoluteRelevanceByPageId() {
        Double relevance = searchIndexRepository.absoluteRelevanceByPageId(testPage.getId());
        assertThat(relevance).isNotNull();
        assertThat(relevance).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("Удаление всех SearchIndex по id страницы")
    void testDeleteAllByPageId() {
        assertThat(searchIndexRepository.findAll()).hasSize(2);

        searchIndexRepository.deleteAllByPageId(testPage.getId());

        assertThat(searchIndexRepository.findAll()).isEmpty();
    }
}
