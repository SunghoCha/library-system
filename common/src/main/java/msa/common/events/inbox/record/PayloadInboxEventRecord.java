package msa.common.events.inbox.record;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import msa.common.domain.base.BaseTimeEntity;
import msa.common.events.inbox.dto.ConsumerRecordMetadata;
import msa.common.events.inbox.dto.InboxEventRecordStatus;
import msa.common.exception.FailureCategory;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@MappedSuperclass
@ToString(exclude = {"payload","lastError"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PayloadInboxEventRecord extends BaseTimeEntity implements Persistable<Long> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Column(name = "aggregate_version", updatable = false) // 사가의 경우 없어도 될 듯 null 허용
    private Long aggregateVersion;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InboxEventRecordStatus inboxEventRecordStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category")
    private FailureCategory failureCategory;

    @Builder.Default
    @Column(name = "seen_count", nullable = false)
    private int seenCount = 1;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "worker_id")
    private String workerId;

    @Column(name = "lease_until", columnDefinition = "datetime(6)")
    private LocalDateTime leaseUntil;

    @Column(name = "lease_id")
    private String leaseId; // 새롭게 추가된 식별자 토큰

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "topic",        column = @Column(name = "topic", nullable = false, length = 255)),
            @AttributeOverride(name = "partitionNo",  column = @Column(name = "partition_no", nullable = false)),
            @AttributeOverride(name = "recordOffset", column = @Column(name = "record_offset", nullable = false))
    })
    private ConsumerRecordMetadata consumerRecordMetadata;

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }

    @Override
    public Long getId() {
        return id;
    }

}

