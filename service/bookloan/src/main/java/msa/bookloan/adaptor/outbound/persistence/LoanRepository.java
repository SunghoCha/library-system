package msa.bookloan.adaptor.outbound.persistence;

import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<BookLoan, Long> {
    boolean existsByMemberIdAndLoanStatus(Long memberId, LoanStatus status);
}
