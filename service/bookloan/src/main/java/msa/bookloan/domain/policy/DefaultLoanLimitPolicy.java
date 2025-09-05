package msa.bookloan.domain.policy;

import lombok.RequiredArgsConstructor;
import msa.common.domain.model.MemberGrade;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultLoanLimitPolicy implements LoanLimitPolicy {

    private static final int DEFAULT_LOAN_LIMIT = 3;
    private static final Map<MemberGrade, Integer> LOAN_LIMITS = Map.of(
            MemberGrade.SILVER, DEFAULT_LOAN_LIMIT,
            MemberGrade.GOLD, 5,
            MemberGrade.PLATINUM, 7
    );

    @Override
    public int maxLoansFor(MemberGrade grade) {
        return LOAN_LIMITS.getOrDefault(grade, DEFAULT_LOAN_LIMIT);
    }
}
