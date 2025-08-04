package searchengine.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
public class LemmaDto {

    private int position;
    private String incomingForm;
    private String normalForm;
}
