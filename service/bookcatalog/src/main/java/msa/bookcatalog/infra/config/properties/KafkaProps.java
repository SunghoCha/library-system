package msa.bookcatalog.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProps {
    private String sourceToken;
    private String topicCatalogChanged;
}
