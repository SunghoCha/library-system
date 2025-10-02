package msa.bookcatalog.infra.aladin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
