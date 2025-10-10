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
                @Index(name = "ix_status_created", columnList = "status, created_at"),                     // 추가
                @Index(name = "ix_agg_stream",     columnList = "aggregate_type, aggregate_id, aggregate_version"),
                @Index(name = "ix_created",        columnList = "created_at")                             // 선택: 유지하거나 제거
        }
)
public class OutboxEventRecord extends PayloadOutboxEventRecord {
}
