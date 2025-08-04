package searchengine.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import searchengine.model.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PageRepositoryTest {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private SearchIndexRepository searchIndexRepository;

    private SiteEntity testSite;
    private LemmaEntity testLemma;

    @BeforeEach
    void setUp() {
        searchIndexRepository.deleteAll();
        pageRepository.deleteAll();
        lemmaRepository.deleteAll();
        siteRepository.deleteAll();

        siteRepository.flush();
        lemmaRepository.flush();
        pageRepository.flush();
        searchIndexRepository.flush();

        testSite = new SiteEntity();
        testSite.setUrl("https://example.com");
        testSite.setName("Example");
        testSite.setStatus(Status.INDEXING);
        testSite.setStatusTime(java.time.LocalDateTime.now());
        siteRepository.save(testSite);

        testLemma = new LemmaEntity();
        testLemma.setSite(testSite);
        testLemma.setLemma("testLemma");
        testLemma.setFrequency(1);
        lemmaRepository.save(testLemma);
    }


    @Test
    @DisplayName("Вставка и обновление страницы, затем поиск по пути и ID сайта")
    void testUpsertAndFindByPathAndSiteId() {
        String path = "/index.html";
        int code = 200;
        String content = "<html>content</html>";

        pageRepository.upsertPage(path, testSite.getId(), code, content);

        Optional<PageEntity> optionalPage = pageRepository.findByPathAndSiteId(path, testSite.getId());
        assertThat(optionalPage).isPresent();

        PageEntity page = optionalPage.get();
        assertThat(page.getPath()).isEqualTo(path);
        assertThat(page.getCode()).isEqualTo(code);
        assertThat(page.getContent()).isEqualTo(content);

        String newContent = "<html>updated</html>";
        int newCode = 201;
        pageRepository.upsertPage(path, testSite.getId(), newCode, newContent);

        optionalPage = pageRepository.findByPathAndSiteId(path, testSite.getId());
        assertThat(optionalPage).isPresent();
        assertThat(optionalPage.get().getCode()).isEqualTo(newCode);
        assertThat(optionalPage.get().getContent()).isEqualTo(newContent);
    }

    @Test
    @DisplayName("Подсчет всех страниц по ID сайта")
    void testCountAllBySiteId() {
        pageRepository.upsertPage("/p1", testSite.getId(), 200, "content1");
        pageRepository.upsertPage("/p2", testSite.getId(), 200, "content2");

        int count = pageRepository.countAllBySiteId(testSite.getId());
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Поиск всех страниц по ID леммы с постраничным выводом")
    void testFindAllByLemmaId() {
        pageRepository.upsertPage("/page1", testSite.getId(), 200, "content");

        PageEntity page = pageRepository.findByPathAndSiteId("/page1", testSite.getId()).orElseThrow();

        SearchIndexEntity index = new SearchIndexEntity();
        index.setPage(page);
        index.setLemma(testLemma);
        index.setLemmaRank(0.9f);
        searchIndexRepository.save(index);

        Page<PageEntity> result = pageRepository.findAllByLemmaId(testLemma.getId(), PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(page.getId());
    }
}
