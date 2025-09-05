package msa.common.events.outbox.record;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.EventType;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.events.outbox.OutboxEventRecordStatus;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@MappedSuperclass
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PayloadOutboxEventRecord {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
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

    @Column(nullable = false)
    private int retryCount = 0;

    private String lastError;

    public void updateOutboxEventRecordStatus(OutboxEventRecordStatus outboxEventRecordStatus) {
        this.outboxEventRecordStatus = outboxEventRecordStatus;
    }

    public void incrementRetryCount() {
        retryCount++;
    }
}

