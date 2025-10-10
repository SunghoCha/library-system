package msa.bookloan.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProps {
    private String sourceToken;

    // topics
    private String topicLoanRequested;
    private String topicLoanReturned;
    private String topicCatalogChanged;

    // groups
    private String groupLoanRequested;
    private String groupCatalogReplica;
}
