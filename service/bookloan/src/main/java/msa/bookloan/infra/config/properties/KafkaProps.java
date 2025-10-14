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
    private String topicPointCharge;
    private String topicPointRefund;      // 보상
    private String topicShippingSchedule;
    private String topicInventoryRelease; // 보상

    // single reply channel
    private String topicSagaReplies; // 응답

    // catalog
    private String topicCatalogChanged;

    // groups
    private String groupSagaReplies;
    private String groupCatalogReplica;
}
