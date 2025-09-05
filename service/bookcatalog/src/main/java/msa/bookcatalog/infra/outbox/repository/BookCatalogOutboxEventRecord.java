package msa.bookcatalog.infra.outbox.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.outbox.record.PayloadOutboxEventRecord;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "book_catalog_outbox")
public class BookCatalogOutboxEventRecord extends PayloadOutboxEventRecord {
}
