package msa.bookcatalog.infra.outbox.repository;

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
        name = "book_catalog_outbox_event_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_outbox_event_id", columnNames = {"event_id"}),
                @UniqueConstraint(name = "uq_outbox_stream",   columnNames = {"aggregate_type","aggregate_id","aggregate_version"})
        },
        indexes = {
                @Index(name = "ix_claim", columnList = "outbox_event_record_status, occurred_at"),
                @Index(name = "ix_agg_stream", columnList = "aggregate_type, aggregate_id, aggregate_version")
        }
)
public class BookCatalogOutboxEventRecord extends PayloadOutboxEventRecord {
}
