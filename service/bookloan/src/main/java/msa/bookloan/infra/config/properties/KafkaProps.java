package msa.bookloan.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProps {
    private String sourceToken;

    // commands
    private String topicMemberCheck;
    private String topicInventoryReserve;

    // single reply channel
    private String topicSagaReplies;

    // catalog
    private String topicCatalogChanged;

    // groups
    private String groupSagaReplies;
    private String groupCatalogReplica;
}
