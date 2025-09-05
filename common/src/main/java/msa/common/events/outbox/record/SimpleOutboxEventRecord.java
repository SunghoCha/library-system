package msa.common.events.outbox.record;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import msa.common.events.EventType;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.events.outbox.OutboxEventRecordStatus;

import java.time.LocalDateTime;

@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SimpleOutboxEventRecord {  // 페이로드 없는 이벤트
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
