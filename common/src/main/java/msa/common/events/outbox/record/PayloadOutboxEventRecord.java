package msa.common.events.outbox.record;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

    public void updateOutboxEventRecordStatus(OutboxEventRecordStatus outboxEventRecordStatus) {
        this.outboxEventRecordStatus = outboxEventRecordStatus;
    }

    public void incrementRetryCount() {
        retryCount++;
    }

    public void handleFailure(String errorMessage, int maxRetryCount) {
        this.retryCount++;
        if (this.retryCount >= maxRetryCount) {
            markAsDeadLetter(errorMessage);
        } else {
            this.outboxEventRecordStatus = OutboxEventRecordStatus.FAILED;
            this.lastError = errorMessage;
        }
    }

    public void markAsPublished() {
        this.outboxEventRecordStatus = OutboxEventRecordStatus.PUBLISHED;
    }

    public void markAsDeadLetter(String errorMessage) {
        this.outboxEventRecordStatus = OutboxEventRecordStatus.DEAD_LETTER;
        this.lastError = errorMessage;
    }

    // updateOutboxEventRecordStatus, incrementRetryCount 메서드는
    // 더 구체적인 새 메서드들로 대체되었으므로 삭제하거나 private으로 변경할 수 있습니다.
}


