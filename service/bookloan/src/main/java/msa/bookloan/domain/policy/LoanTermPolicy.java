package msa.bookloan.domain.policy;

import msa.common.domain.model.BookCategory;

public interface LoanTermPolicy {
    Long loanPeriodFor(BookCategory category);
}
