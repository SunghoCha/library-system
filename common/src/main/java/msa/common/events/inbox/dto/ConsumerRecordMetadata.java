package msa.common.events.inbox.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.NoArgsConstructor;


@Embeddable
@NoArgsConstructor
public class ConsumerRecordMetadata {

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(nullable = false)
    private Integer partition;

    @Column(nullable = false)
    private Long offset;

    @Builder
    public ConsumerRecordMetadata(String topic, Integer partition, Long offset) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
    }
}
