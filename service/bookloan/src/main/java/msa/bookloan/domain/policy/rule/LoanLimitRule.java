package msa.bookloan.domain.policy.rule;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.policy.LoanLimitPolicy;
import msa.bookloan.application.service.dto.LoanContext;
import msa.bookloan.application.service.exception.LoanLimitExceededException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoanLimitRule implements LoanValidationRule {

    private final BookLoanRepository bookLoanRepository;
    private final LoanLimitPolicy loanLimitPolicy;

    @Override
    public void validate(LoanContext context) {
        int currentlyLoanedCount = bookLoanRepository.countByMemberIdAndLoanStatusIn(context.memberId(),
                List.of(LoanStatus.LOANED));
        int requestLoanedCount = context.bookIds().size();
        int maxAllowedLoans = loanLimitPolicy.maxLoansFor(context.memberGrade());

        if (currentlyLoanedCount + requestLoanedCount > maxAllowedLoans) {
            throw new LoanLimitExceededException(context.memberId());
        }
    }
}
