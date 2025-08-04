package searchengine.repositories;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class LemmaRepositoryTest {

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private SiteRepository siteRepository;

    private SiteEntity testSite;
    private LemmaEntity testLemma;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        lemmaRepository.deleteAll();
        siteRepository.deleteAll();
        siteRepository.flush();
        lemmaRepository.flush();


        testSite = new SiteEntity();
        testSite.setUrl("https://example.com");
        testSite.setName("Example");
        testSite.setStatus(Status.INDEXING);
        testSite.setStatusTime(LocalDateTime.now());
        siteRepository.save(testSite);


        testLemma = new LemmaEntity();
        testLemma.setSite(testSite);
        testLemma.setLemma("testLemma");
        testLemma.setFrequency(5);
        lemmaRepository.save(testLemma);
    }

    @Test
    @DisplayName("Поиск леммы по siteId и тексту леммы")
    void testFindBySiteIdAndLemma() {
        Optional<LemmaEntity> found = lemmaRepository.findBySiteIdAndLemma(testSite.getId(), "testLemma");
        assertThat(found).isPresent();
        assertThat(found.get().getFrequency()).isEqualTo(5);
    }

    @Test
    @DisplayName("Уменьшение frequency леммы")
    void testDecrementAllFrequencyBySiteIdAndLemma() {
        lemmaRepository.decrementAllFrequencyBySiteIdAndLemma(testSite.getId(), "testLemma");
        entityManager.flush();
        entityManager.clear();
        Optional<LemmaEntity> lemma = lemmaRepository.findBySiteIdAndLemma(testSite.getId(), "testLemma");
        assertThat(lemma).isPresent();
        assertThat(lemma.get().getFrequency()).isEqualTo(4);
    }

    @Test
    @DisplayName("Подсчет всех лемм по siteId")
    void testCountAllBySiteId() {
        LemmaEntity anotherLemma = new LemmaEntity();
        anotherLemma.setSite(testSite);
        anotherLemma.setLemma("another");
        anotherLemma.setFrequency(2);
        lemmaRepository.save(anotherLemma);

        int count = lemmaRepository.countAllBySiteId(testSite.getId());
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Upsert леммы: вставка новой и обновление существующей")
    void testUpsertLemma() {
        lemmaRepository.upsertLemma("newLemma", testSite.getId());
        entityManager.flush();
        entityManager.clear();
        Optional<LemmaEntity> newLemma = lemmaRepository.findBySiteIdAndLemma(testSite.getId(), "newLemma");
        assertThat(newLemma).isPresent();
        assertThat(newLemma.get().getFrequency()).isEqualTo(1);

        lemmaRepository.upsertLemma("newLemma", testSite.getId());
        entityManager.flush();
        entityManager.clear();
        newLemma = lemmaRepository.findBySiteIdAndLemma(testSite.getId(), "newLemma");
        assertThat(newLemma).isPresent();
        assertThat(newLemma.get().getFrequency()).isEqualTo(2);
    }
}
