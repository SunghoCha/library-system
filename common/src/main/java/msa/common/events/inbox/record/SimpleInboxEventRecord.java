package msa.common.events.inbox.record;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import msa.common.events.inbox.dto.InboxEventRecordStatus;

import java.time.LocalDateTime;

@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SimpleInboxEventRecord {  // 페이로드 없는 이벤트

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private Long eventId;

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
    private msa.common.events.inbox.dto.ConsumerRecordMetadata ConsumerRecordMetadata;

    private String lastError;

    public SimpleInboxEventRecord(Long id, Long eventId, LocalDateTime receivedAt,
                                  InboxEventRecordStatus inboxEventRecordStatus, int retryCount,
                                  msa.common.events.inbox.dto.ConsumerRecordMetadata consumerRecordMetadata, String lastError) {
        this.id = id;
        this.eventId = eventId;
        this.receivedAt = receivedAt;
        this.inboxEventRecordStatus = inboxEventRecordStatus;
        this.retryCount = retryCount;
        ConsumerRecordMetadata = consumerRecordMetadata;
        this.lastError = lastError;
    }
}
