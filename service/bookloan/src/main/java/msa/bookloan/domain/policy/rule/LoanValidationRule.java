package msa.bookloan.domain.policy.rule;

import msa.bookloan.application.service.dto.LoanContext;

public interface LoanValidationRule {
    void validate(LoanContext context);
}
