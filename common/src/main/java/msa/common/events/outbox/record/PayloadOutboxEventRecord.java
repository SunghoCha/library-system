package msa.common.events.outbox.record;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import msa.common.domain.base.BaseTimeEntity;
import msa.common.events.EventType;
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

    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id",   nullable = false, length = 191)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType eventType;

    @Column(name = "payload", columnDefinition = "json")
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventRecordStatus outboxEventRecordStatus;

    @Builder.Default
    @Column(nullable = false)
    private int retryCount = 0;

    @Setter
    @Column(name = "worker_id", length = 64)
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
            @AttributeOverride(name = "topic",        column = @Column(name = "topic", nullable = false, length = 255)),
            @AttributeOverride(name = "partitionKey", column = @Column(name = "partition_key", nullable = false, length = 255)),
            @AttributeOverride(name = "partition",    column = @Column(name = "partition_no"))
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


