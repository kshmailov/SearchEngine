package searchengine.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
public class SearchResponse {

    private boolean result;
    private int count;
    private List<SearchDto> data;
    private String error;
}
