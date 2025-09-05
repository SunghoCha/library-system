package msa.bookloan.repository;

import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanRepository extends JpaRepository<BookLoan, Long> {

    boolean existsByMemberIdAndLoanStatus(Long memberId, LoanStatus status);
    int countByMemberId(Long memberId);
    List<BookLoan> findByMemberIdAndLoanStatusIn(Long memberId, List<LoanStatus> loanStatuses);
    int countByMemberIdAndLoanStatusIn(Long memberId, List<LoanStatus> loanStatuses);
}

