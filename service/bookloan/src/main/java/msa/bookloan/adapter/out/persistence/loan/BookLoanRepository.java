package msa.bookloan.adapter.out.persistence.loan;

import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookLoanRepository extends JpaRepository<BookLoan, Long> {

    boolean existsByMemberIdAndLoanStatus(Long memberId, LoanStatus status);
    int countByMemberId(Long memberId);
    List<BookLoan> findByMemberIdAndLoanStatusIn(Long memberId, List<LoanStatus> loanStatuses);
    int countByMemberIdAndLoanStatusIn(Long memberId, List<LoanStatus> loanStatuses);

    @Query("SELECT EXISTS(" +
            "SELECT 1 " +
            "FROM BookLoan bl " +
            "WHERE bl.memberId = :memberId AND bl.loanStatus = 'LOANED' AND bl.dueDate < :today)"
    )
    boolean existsOverdueLoan(@Param("memberId") Long memberId, @Param("today") LocalDate today);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE BookLoan b
           SET b.currentSagaId = :sagaId
         WHERE b.id = :loanId
           AND (b.currentSagaId IS NULL OR b.currentSagaId = :sagaId)
        """)
    int tryBindSaga(@Param("loanId") Long loanId,
                    @Param("sagaId") String sagaId);

    // 터미널 전이 시 해제: 내가 건 값일 때만 null
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE BookLoan b
           SET b.currentSagaId = NULL
         WHERE b.id = :loanId
           AND b.currentSagaId = :sagaId
        """)
    int clearSagaIfMatches(@Param("loanId") Long loanId,
                           @Param("sagaId") Long sagaId);

    // 취소 진입 시 조회용
    @Query("select b.currentSagaId from BookLoan b where b.id = :loanId")
    Optional<String> findCurrentSagaId(@Param("loanId") Long loanId);
}

