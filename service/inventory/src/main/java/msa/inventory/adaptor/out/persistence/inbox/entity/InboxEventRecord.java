package msa.inventory.adaptor.out.persistence.inbox.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
                @UniqueConstraint(name = "uk_event", columnNames = {"event_id"}),
                @UniqueConstraint(name = "uk_pos",   columnNames = {"topic", "partition_no", "record_offset"})
        },
        indexes = {
                @Index(name = "idx_event_status_lease", columnList = "event_id, status, lease_id"),
                @Index(name = "idx_status_lease", columnList = "status, lease_until"),
        }
)
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEventRecord extends PayloadInboxEventRecord {

}
