package msa.bookloan.adapter.out.persistence.saga;

import msa.bookloan.domain.saga.LoanSaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanSagaRepository extends JpaRepository<LoanSaga, String> {

    boolean existsBySagaId(String sagaId);

    Optional<LoanSaga> findBySagaId(String sagaId);
}
