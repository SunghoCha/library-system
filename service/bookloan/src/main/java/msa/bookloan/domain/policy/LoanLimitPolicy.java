package msa.bookloan.domain.policy;

import msa.common.domain.model.MemberGrade;

public interface LoanLimitPolicy {
    int maxLoansFor(MemberGrade grade);
}
