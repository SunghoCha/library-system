package msa.bookcatalog.infra.aladin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
//@ConfigurationProperties(prefix = "aladin.ttb")
public class AladinProperties {

    @Value("${aladin.ttb.key}")
    private String key;
    private final String version = "20131101";
    private final String format = "js";
    private final String searchTarget = "Book";
    private final int defaultMaxResults = 50;
    private final int defaultStart = 1;


}
