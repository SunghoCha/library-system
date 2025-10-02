package msa.bookloan.infra.inbox.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.inbox.record.PayloadInboxEventRecord;


@Entity
@SuperBuilder
@Table(
        name = "book_catalog_inbox",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inbox_event_id", columnNames = "event_id")
        },
        indexes = {
                @Index(name="idx_inbox_status",   columnList="inbox_event_record_status"),
                @Index(name = "idx_inbox_status_last_seen", columnList = "inbox_event_record_status, last_seen_at"),
                @Index(name= "idx_inbox_src",      columnList="topic, partition_no, record_offset"),
                @Index(name = "idx_agg_status", columnList = "aggregate_id, inbox_event_record_status, last_seen_at")
        }
)
public class BookCatalogProjectionInboxEventRecord extends PayloadInboxEventRecord {

    public BookCatalogProjectionInboxEventRecord() {

    }

}
