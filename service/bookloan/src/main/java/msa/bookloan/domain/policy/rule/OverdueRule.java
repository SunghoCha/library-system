package msa.bookloan.domain.policy.rule;

import lombok.RequiredArgsConstructor;
import msa.bookloan.repository.LoanRepository;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.service.dto.LoanContext;
import msa.bookloan.service.exception.LoanOverdueException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

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


