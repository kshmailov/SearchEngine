package searchengine.dto;

import lombok.Data;

@Data
public class IndexResponse {
    private boolean result;
    private String error;
}
