package msa.bookloan.adapter.out.persistence.saga.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import msa.bookloan.domain.saga.LoanSaga;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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


    @Deprecated // 직관적이지 않음. flush 후 행에 베타락걸리는 메커니즘까지 고려해야하는데 코드읽고 바로 받아들이기 힘든것같음
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE loan_saga
               SET status      = 'CANCEL_REQUESTED',
                   worker_id   = NULL,
                   lease_until = NULL,
                   version     = version + 1
             WHERE saga_id     = :id
               AND status NOT IN ('COMPLETED','FAILED','CANCELLED','TIMED_OUT','CANCEL_REQUESTED')
               AND current_step <> 'FINISHED'
            """, nativeQuery = true)
    int requestCancelGate(@Param("id") String sagaId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")
    })
    @Query("select s from LoanSaga s where s.sagaId = :id")
    Optional<LoanSaga> findForUpdate(@Param("id") String sagaId);


    @Query(value = """
                SELECT s.saga_id
                  FROM loan_saga s
                 WHERE s.status = 'PROCESSING'
                   AND s.step_deadline_at IS NOT NULL
                   AND s.step_deadline_at <= :now
                   AND (s.worker_id IS NULL OR s.lease_until < :now)
                 ORDER BY s.step_deadline_at ASC
                 LIMIT :batch
                 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> lockTimedOutProcessingIds(@Param("now") LocalDateTime now,
                                           @Param("batch") int batch);


    @Modifying
    @Query(value = """
                UPDATE loan_saga
                   SET worker_id   = :workerId,
                       lease_until = :leaseUntil
                 WHERE saga_id IN (:ids)
            """, nativeQuery = true)
    int claimByIds(
            @Param("ids") List<String> ids,
            @Param("workerId") String workerId,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Query(value = """
            SELECT s.saga_id
              FROM loan_saga s
             WHERE s.status = 'COMPENSATING'
               AND s.step_deadline_at IS NOT NULL
               AND s.step_deadline_at <= :now
               AND (s.worker_id IS NULL OR s.lease_until < :now)
             ORDER BY s.step_deadline_at ASC
             LIMIT :batch
             FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<String> lockTimedOutCompensatingIds(@Param("now") LocalDateTime now,
                                             @Param("batch") int batch);

}
