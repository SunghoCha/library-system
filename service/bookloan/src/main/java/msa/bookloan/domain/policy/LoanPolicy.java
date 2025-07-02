package msa.bookloan.domain.policy;

import msa.bookloan.dto.LoanCommand;

public interface LoanPolicy {
    void check(LoanCommand command);
}
