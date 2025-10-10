package msa.bookloan.adapter.out.persistence.outbox.repository;

import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Set;

public interface OutboxEventRecordRepository extends JpaRepository<OutboxEventRecord, Long> {
    @Query("select e.eventId from OutboxEventRecord e where e.eventId in :ids")
    Set<Long> findExistingEventIdsByEventIdIn(Collection<Long> ids);
}
