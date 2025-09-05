package msa.common.events.inbox.record;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.EventType;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@MappedSuperclass
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PayloadInboxEventRecord {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InboxEventRecordStatus inboxEventRecordStatus;

    @Version
    private Long version;

    @Column(nullable = false)
    private int retryCount = 0;

    @Embedded
    private ConsumerRecordMetadata consumerRecordMetadata;

    private String lastError;

    public PayloadInboxEventRecord(Long id, Long eventId, String payload, LocalDateTime receivedAt,
                                   InboxEventRecordStatus inboxEventRecordStatus,
                                   ConsumerRecordMetadata consumerRecordMetadata, String lastError) {
        this.id = id;
        this.eventId = eventId;
        this.payload = payload;
        this.receivedAt = receivedAt;
        this.inboxEventRecordStatus = inboxEventRecordStatus;
        this.consumerRecordMetadata = consumerRecordMetadata;
        this.lastError = lastError;
    }

    public void updateInboxEventRecordStatus(InboxEventRecordStatus inboxEventRecordStatus) {
        this.inboxEventRecordStatus = inboxEventRecordStatus;
    }

    public void incrementRetryCount() {
        retryCount++;
    }
}

