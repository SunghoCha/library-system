package msa.common.events.outbox.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Builder;

@Builder
@Embeddable
public record OutboxRouting(
        @Column(name = "topic", nullable = false) String topic,
        @Column(name = "partition_key", nullable = false) String partitionKey
) { }
