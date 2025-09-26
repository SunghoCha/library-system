package msa.common.events.outbox.record;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import msa.common.events.EventType;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.dto.OutboxRouting;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@MappedSuperclass
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PayloadOutboxEventRecord {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long eventId;

    private String aggregateType;
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventRecordStatus outboxEventRecordStatus;

    @Version
    private Long version;

    @Builder.Default
    @Column(nullable = false)
    private int retryCount = 0;

    @Setter
    @Column
    private LocalDateTime pickedAt;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "topic",        column = @Column(name = "topic", nullable = false, length = 255)),
            @AttributeOverride(name = "partitionKey", column = @Column(name = "partition_key", nullable = false, length = 255)),
            @AttributeOverride(name = "partition",    column = @Column(name = "partition_no"))
    })
    private OutboxRouting routing;

}


