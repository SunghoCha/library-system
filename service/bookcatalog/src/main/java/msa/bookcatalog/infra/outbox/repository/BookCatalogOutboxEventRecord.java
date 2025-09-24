package msa.bookcatalog.infra.outbox.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.outbox.record.PayloadOutboxEventRecord;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "book_catalog_outbox", indexes = {
        @Index(name = "idx_outbox_status_occurred_at", columnList = "outboxEventRecordStatus, occurredAt")
})
public class BookCatalogOutboxEventRecord extends PayloadOutboxEventRecord {
}
