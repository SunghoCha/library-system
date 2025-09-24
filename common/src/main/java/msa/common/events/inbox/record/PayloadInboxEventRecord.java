package msa.common.events.inbox.record;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.EventType;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.exception.FailureCategory;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PayloadInboxEventRecord {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private long aggregateVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private EventType eventType;

    @Lob
    @Column(name = "payload")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "inbox_event_record_status", nullable = false, length = 50)
    private InboxEventRecordStatus inboxEventRecordStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 32)
    private FailureCategory failureCategory;

    @Builder.Default
    @Column(name = "seen_count", nullable = false)
    private int seenCount = 1;

    @Column(name = "first_seen_at", nullable = false, columnDefinition = "datetime(6)", updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime lastSeenAt;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "topic",        column = @Column(name = "topic", nullable = false, length = 255)),
            @AttributeOverride(name = "partitionNo",  column = @Column(name = "partition_no", nullable = false)),
            @AttributeOverride(name = "recordOffset", column = @Column(name = "record_offset", nullable = false))
    })
    private ConsumerRecordMetadata consumerRecordMetadata;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PayloadInboxEventRecord(Long id, Long eventId, Long aggregateId, Long aggregateVersion,
                                   EventType eventType, String payload,
                                   InboxEventRecordStatus inboxEventRecordStatus,
                                   ConsumerRecordMetadata consumerRecordMetadata) {
        this.id = id;
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.eventType = eventType;
        this.payload = payload;
        this.inboxEventRecordStatus = inboxEventRecordStatus;
        this.consumerRecordMetadata = consumerRecordMetadata;
    }

    public void updateInboxEventRecordStatus(InboxEventRecordStatus inboxEventRecordStatus) {
        this.inboxEventRecordStatus = inboxEventRecordStatus;
    }

    public void incrementRetryCount() {
        retryCount++;
    }
}

