package msa.bookcatalog.infra.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.aladin.ttb")
public class AladinProperties {

    private String key;
    private String version = "20131101";
    private String format = "js";
    private String searchTarget = "Book";
    private int defaultMaxResults = 50;
    private int defaultStart = 1;


}
