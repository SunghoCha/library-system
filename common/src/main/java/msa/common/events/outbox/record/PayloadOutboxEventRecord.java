package msa.common.events.outbox.record;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import msa.common.domain.base.BaseTimeEntity;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PayloadOutboxEventRecord extends BaseTimeEntity implements Persistable<Long> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id",   nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_version", updatable = false) // 사가커맨드는 null 허용
    private Long aggregateVersion;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "json")
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxEventRecordStatus outboxEventRecordStatus;

    @Column(name = "failure_category", length = 32)
    private String failureCategory;

    @Builder.Default
    @Column(nullable = false)
    private int retryCount = 0;

    @Setter
    @Column(name = "worker_id")
    private String workerId;

    @Setter
    @Column(name = "lease_until", columnDefinition = "datetime(6)")
    private LocalDateTime leaseUntil;

    @Setter
    @Column(name = "picked_at", columnDefinition = "datetime(6)")
    private LocalDateTime pickedAt;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "topic",        column = @Column(name = "topic", nullable = false)),
            @AttributeOverride(name = "partitionKey", column = @Column(name = "partition_key", nullable = false)),
    })
    private OutboxRouting routing;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @PrePersist
    protected void onCreateDefaults() {
        LocalDateTime now = LocalDateTime.now();
        if (this.occurredAt == null) this.occurredAt = now;
    }

}


