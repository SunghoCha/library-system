package msa.bookloan.adapter.out.persistence.outbox.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.outbox.record.PayloadOutboxEventRecord;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "outbox_event_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_outbox_event_id", columnNames = {"event_id"})
        },
        indexes = {
                // new, failed용
                @Index(name = "ix_status_occurred_id", columnList = "status, occurred_at, id"),                     // 추가
                // 사가 조회용
                @Index(name = "ix_outbox_aggtype_aggid",     columnList = "aggregate_type, aggregate_id"),
                // publishing 좀비 회수용
                @Index(name = "ix_status_picked_lease_id",        columnList = "status, picked_at, lease_until, id")                             // 선택: 유지하거나 제거
        }
)
public class OutboxEventRecord extends PayloadOutboxEventRecord {
}
