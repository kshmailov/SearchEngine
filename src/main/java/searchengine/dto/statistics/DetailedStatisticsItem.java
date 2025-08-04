package searchengine.dto.statistics;

import lombok.Data;
import searchengine.model.Status;

import java.time.LocalDateTime;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private Status status;
    private LocalDateTime statusTime;
    private String error;
    private int pages;
    private int lemmas;
}
