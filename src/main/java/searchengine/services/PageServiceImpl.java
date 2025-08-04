package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.services.interfaces.PageService;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageServiceImpl implements PageService {

    private final PageRepository pageRepository;

    @Override
    @Transactional
    public PageEntity createOrUpdatePage(SiteEntity site, String path, int statusCode, String html) {
        log.info("💾 Запрос на upsert страницы: siteId={}, path='{}', statusCode={}", site.getId(), path, statusCode);

        pageRepository.upsertPage(path, site.getId(), statusCode, html);

        Optional<PageEntity> result = pageRepository.findByPathAndSiteId(path, site.getId());
        if (result.isPresent()) {
            log.info("✅ Страница найдена/сохранена: id={}, siteId={}, path='{}'",
                    result.get().getId(), site.getId(), path);
        } else {
            log.warn("⚠️ После upsert не найдена страница: siteId={}, path='{}'", site.getId(), path);
        }

        return result.orElseThrow(() ->
                new IllegalStateException("Страница не найдена после upsert: path=" + path + ", siteId=" + site.getId()));
    }
}
