package msa.bookloan.domain.policy.rule;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.persistence.loan.LoanRepository;
import msa.bookloan.application.service.dto.LoanContext;
import msa.bookloan.application.service.exception.LoanOverdueException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class OverdueRule implements LoanValidationRule {

    private final LoanRepository loanRepository;

    @Override
    public void validate(LoanContext context) {
        boolean hasOverdue = loanRepository.existsOverdueLoan(
                context.memberId(),
                LocalDate.now()
        );

        if (hasOverdue) {
            throw new LoanOverdueException(context.memberId());
        }
    }
}


