package msa.bookloan.domain.policy;

public interface LoanTermPolicy {
    Long loanPeriodFor(BookType category);
}
