package searchengine.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
public class SearchResponse {

    private boolean result;
    private long count;
    private List<SearchDto> data;
    private String error;
}
