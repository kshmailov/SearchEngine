package searchengine.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SiteRepositoryTest {

    @Autowired
    private SiteRepository siteRepository;
    @BeforeEach
    void cleanDatabase() {
        siteRepository.deleteAll();
    }

    @Test
    @DisplayName("Сохраняет и находит сущность по URL")
    void testSaveAndFindByUrl() {
        SiteEntity site = new SiteEntity();
        site.setUrl("https://example.com");
        site.setName("Example");
        site.setStatus(Status.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        SiteEntity found = siteRepository.findByUrl("https://example.com");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Example");
        assertThat(found.getStatus()).isEqualTo(Status.INDEXED);
    }

    @Test
    @DisplayName("Проверяет существование сайта по URL и статусу")
    void testExistsByUrlAndStatus() {
        SiteEntity site = new SiteEntity();
        site.setUrl("https://test.com");
        site.setName("Test");
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        assertThat(siteRepository.existsByUrl("https://test.com")).isTrue();
        assertThat(siteRepository.existsByStatus(Status.INDEXING)).isTrue();
        assertThat(siteRepository.existsByStatus(Status.FAILED)).isFalse();
    }

    @Test
    @DisplayName("Находит список сайтов по статусу")
    void testFindByStatusAndFindAllByStatus() {
        SiteEntity site1 = new SiteEntity();
        site1.setUrl("https://a.com");
        site1.setName("A");
        site1.setStatus(Status.INDEXED);
        site1.setStatusTime(LocalDateTime.now());

        SiteEntity site2 = new SiteEntity();
        site2.setUrl("https://b.com");
        site2.setName("B");
        site2.setStatus(Status.INDEXED);
        site2.setStatusTime(LocalDateTime.now());

        siteRepository.save(site1);
        siteRepository.save(site2);

        List<SiteEntity> sites = siteRepository.findByStatus(Status.INDEXED);
        assertThat(sites).hasSize(2).extracting("url").containsExactlyInAnyOrder("https://a.com", "https://b.com");

        Optional<List<SiteEntity>> optionalSites = siteRepository.findAllByStatus(Status.INDEXED);
        assertThat(optionalSites).isPresent();
        assertThat(optionalSites.get()).hasSize(2);
    }

    @Test
    @DisplayName("Удаляет сайт по URL")
    void testDeleteByUrl() {
        SiteEntity site = new SiteEntity();
        site.setUrl("https://delete.com");
        site.setName("Delete");
        site.setStatus(Status.INDEXING); // пример
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);


        assertThat(siteRepository.existsByUrl("https://delete.com")).isTrue();

        siteRepository.deleteByUrl("https://delete.com");

        SiteEntity found = siteRepository.findByUrl("https://delete.com");
        assertThat(found).isNull();
        assertThat(siteRepository.existsByUrl("https://delete.com")).isFalse();
    }
}
