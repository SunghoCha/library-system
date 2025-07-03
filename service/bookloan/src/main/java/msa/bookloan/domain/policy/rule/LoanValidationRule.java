package msa.bookloan.domain.policy.rule;

import msa.bookloan.dto.LoanContext;

public interface LoanValidationRule {
    void validate(LoanContext context);
}
