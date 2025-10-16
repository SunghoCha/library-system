package msa.bookloan.adapter.out.persistence.saga;

import msa.bookloan.domain.saga.LoanSaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LoanSagaRepository extends JpaRepository<LoanSaga, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO loan_saga (
               saga_id, loan_id, member_id, book_id,
               aggregate_version, trigger_event_id,
               status, current_step, row_version,
               step_started_at, step_deadline_at
            )
            VALUES (:sagaId, :loanId, :memberId, :bookId,
                    :aggregateVersion, :triggerEventId,
                    :status, :step, 0,
                    NOW(6), :deadline)
            ON DUPLICATE KEY UPDATE saga_id = saga_id
            """, nativeQuery = true)
    int insertIfAbsentRaw(@Param("sagaId") String sagaId,
                          @Param("loanId") Long loanId,
                          @Param("memberId") Long memberId,
                          @Param("bookId") Long bookId,
                          @Param("aggregateVersion") Long aggregateVersion,
                          @Param("triggerEventId") Long triggerEventId,
                          @Param("status") String status,
                          @Param("step") String step,
                          @Param("deadline") LocalDateTime deadline);

    default boolean insertIfAbsent(String sagaId,
                                   Long loanId,
                                   Long memberId,
                                   Long bookId,
                                   Long aggregateVersion,
                                   Long triggerEventId,
                                   String status,
                                   String step,
                                   LocalDateTime deadline) {
        int affected = insertIfAbsentRaw(
                sagaId, loanId, memberId, bookId, aggregateVersion,
                triggerEventId, status, step, deadline
        );
        return affected == 1; // 1=INSERT(신규 시작), 0=중복(NOP)
    }
}
