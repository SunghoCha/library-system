package msa.bookloan.infra.messaging.inbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import msa.common.events.inbox.record.PayloadInboxEventRecord;

@Getter
@Entity
@Table(
        name = "inbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_src_event", columnNames = {"source", "event_id"})
        },
        indexes = {
                @Index(name="idx_inbox_status_next", columnList="inbox_event_record_status, next_attempt_at"),
                @Index(name="idx_inbox_src",        columnList="topic, partition_no, record_offset"),
                @Index(name="idx_agg_status",       columnList="aggregate_id, inbox_event_record_status, last_seen_at")
        }
)
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEventRecord extends PayloadInboxEventRecord {

    @Column(name = "source", nullable = false, length = 64) // "book-catalog","inventory","payment"
    private String source;


}
