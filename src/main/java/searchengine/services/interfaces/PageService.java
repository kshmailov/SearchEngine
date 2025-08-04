package searchengine.services.interfaces;

import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface PageService {
    PageEntity createOrUpdatePage(SiteEntity site, String path, int statusCode, String html);
}
