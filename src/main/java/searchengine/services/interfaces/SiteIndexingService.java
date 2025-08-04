package searchengine.services.interfaces;

import searchengine.dto.IndexResponse;

import java.io.IOException;

public interface SiteIndexingService {

    IndexResponse startFullIndexing();
    IndexResponse stopFullIndexing();
    IndexResponse indexPage(String url) throws IOException;
}
