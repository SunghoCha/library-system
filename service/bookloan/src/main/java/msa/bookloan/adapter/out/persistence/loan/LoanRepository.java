package msa.bookloan.adapter.out.persistence.loan;

import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LoanRepository extends JpaRepository<BookLoan, Long> {

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
}

