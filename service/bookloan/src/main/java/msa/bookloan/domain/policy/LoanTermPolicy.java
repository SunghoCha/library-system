package msa.bookloan.domain.policy;

import msa.bookloan.domain.model.BookType;

public interface LoanTermPolicy {
    Long loanPeriodFor(BookType bookType);
}
