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
        }, // TODO : 인덱스 점검하기
        indexes = {
                @Index(name = "ix_status_occurred_id",    columnList = "status, occurred_at, id"),
                @Index(name = "ix_status_retry",          columnList = "status, retry_count"),
                @Index(name = "ix_status_lease",          columnList = "status, lease_until"),
                @Index(name = "ix_outbox_aggtype_aggid",  columnList = "aggregate_type, aggregate_id")
        }
)
public class OutboxEventRecord extends PayloadOutboxEventRecord {
}
