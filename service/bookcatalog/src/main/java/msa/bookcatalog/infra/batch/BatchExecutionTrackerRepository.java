package msa.bookcatalog.infra.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchExecutionTrackerRepository extends JpaRepository<BatchExecutionTracker, String> {
}
