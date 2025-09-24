package msa.common.events.outbox.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
public class OutboxRouting {

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 255)
    private String partitionKey;

    @Builder
    public OutboxRouting(String topic, String partitionKey) {
        this.topic = topic;
        this.partitionKey = partitionKey;
    }
}
