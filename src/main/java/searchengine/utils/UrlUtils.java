package searchengine.utils;

public class UrlUtils {
    public static String normalizeBaseUrl(String url) {
        if (url == null) return null;
        url = url.replace("www.", "");
        while (url.endsWith("/") && url.length() > 8) { // минимальная длина: https://x
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
