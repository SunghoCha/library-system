package msa.bookcatalog.infra.batch.aladin.repository;

import msa.bookcatalog.infra.batch.aladin.BatchExecutionTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchExecutionTrackerRepository extends JpaRepository<BatchExecutionTracker, String> {
}
