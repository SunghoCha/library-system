package msa.bookloan.domain.policy;

import msa.common.domain.BookCategory;

import java.time.Duration;

public interface LoanTermPolicy {
    Duration loanPeriodFor(BookCategory category);
}
