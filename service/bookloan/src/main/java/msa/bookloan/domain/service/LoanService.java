package msa.bookloan.domain.service;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adaptor.outbound.persistence.LoanRepository;
import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.policy.LoanLimitPolicy;
import msa.bookloan.domain.policy.LoanTermPolicy;
import msa.bookloan.domain.policy.rule.LoanValidationRule;
import msa.bookloan.dto.LoanCommand;
import msa.bookloan.dto.LoanContext;
import msa.bookloan.exception.LoanLimitExceededException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;
    private final List<LoanValidationRule> rules;
    private final LoanLimitPolicy loanLimitPolicy;
    private final LoanTermPolicy loanTermPolicy;

    public void loanBooks(LoanCommand command) {
        // 검증용 컨텍스트 객체
        LoanContext context = LoanContext.builder()
                .memberId(command.memberId())
                .memberGrade(command.memberGrade())
                .bookIds(command.bookIds())
                .build();

        rules.forEach(rule -> rule.validate(context));

        List<BookLoan> bookLoans = command.bookIds().stream()
                .map(id -> BookLoan.builder()
                        .memberId(context.memberId())
                        .bookId(id)
                        .loanStatus(LoanStatus.LOANED)
                        .bookCategory()
                        .loanDate(LocalDate.now())
                        .dueDate(LocalDate.now().plus(loanTermPolicy.loanPeriodFor()))
                        .returnDate()
                        .build())
                .toList();

        // 레파지토리 저장 수행...


        List<BookLoan> results = loanRepository.saveAll(bookLoans);

        return


    }
}
