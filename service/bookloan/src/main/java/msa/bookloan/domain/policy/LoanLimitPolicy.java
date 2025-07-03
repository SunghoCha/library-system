package msa.bookloan.domain.policy;

import msa.common.domain.MemberGrade;

public interface LoanLimitPolicy {
    int maxLoansFor(MemberGrade grade);
}
