package msa.bookloan.domain.policy.rule;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adaptor.outbound.persistence.LoanRepository;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.policy.LoanLimitPolicy;
import msa.bookloan.dto.LoanContext;
import msa.bookloan.exception.LoanLimitExceededException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoanLimitRule implements LoanValidationRule {

    private final LoanRepository loanRepository;
    private final LoanLimitPolicy loanLimitPolicy;

    @Override
    public void validate(LoanContext context) {
        int currentlyLoanedCount = loanRepository.countByMemberIdAndLoanStatusIn(context.memberId(),
                List.of(LoanStatus.LOANED, LoanStatus.OVERDUE));
        int requestLoanedCount = context.bookIds().size();
        int maxAllowedLoans = loanLimitPolicy.maxLoansFor(context.memberGrade());

        if (currentlyLoanedCount + requestLoanedCount > maxAllowedLoans) {
            throw new LoanLimitExceededException();
        }
    }
}
