package msa.common.events.inbox.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor
public class ConsumerRecordMetadata {

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(name = "partition_no", nullable = false)
    private int partition;

    @Column(name = "record_offset", nullable = false)
    private long offset;

    @Builder
    public ConsumerRecordMetadata(String topic, Integer partition, Long offset) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
    }
}
