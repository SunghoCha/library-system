package msa.bookloan.adapter.out.persistence.loan;

import java.time.LocalDate;

public interface BookLoanRepositoryCustom {
    long countOverdue(Long memberId, LocalDate today);
    long countActiveByMember(Long memberId);
}
