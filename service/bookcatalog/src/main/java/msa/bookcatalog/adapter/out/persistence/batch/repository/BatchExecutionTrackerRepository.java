package msa.bookcatalog.adapter.out.persistence.batch.repository;

import msa.bookcatalog.adapter.out.persistence.batch.entity.BatchExecutionTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchExecutionTrackerRepository extends JpaRepository<BatchExecutionTracker, String> {
}
