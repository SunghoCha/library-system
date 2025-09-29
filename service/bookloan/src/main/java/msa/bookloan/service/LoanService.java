package msa.bookloan.service;

import lombok.RequiredArgsConstructor;
import msa.bookloan.infra.projection.repository.BookCatalogProjectionRepository;
import msa.bookloan.repository.LoanRepository;
import msa.bookloan.infra.projection.BookCatalogProjection;
import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.policy.LoanTermPolicy;
import msa.bookloan.domain.policy.rule.LoanValidationRule;
import msa.bookloan.service.dto.LoanCommand;
import msa.bookloan.service.dto.LoanContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;
    private final List<LoanValidationRule> rules;
    private final LoanTermPolicy loanTermPolicy;
    private final BookCatalogProjectionRepository bookCatalogProjectionRepository;

    public void loanBooks(LoanCommand command) {
        // 검증용 컨텍스트 객체
        LoanContext context = LoanContext.builder()
                .memberId(command.memberId())
                .memberGrade(command.memberGrade())
                .bookIds(command.bookIds())
                .build();

        rules.forEach(rule -> rule.validate(context));

        Map<Long, BookCatalogProjection> infoMap = bookCatalogProjectionRepository.findAllById(command.bookIds())
                .stream()
                .collect(Collectors.toMap(BookCatalogProjection::getBookId, Function.identity()));


        List<BookLoan> bookLoans = command.bookIds().stream()
                .map(id -> {
                    BookCatalogProjection bookCatalogProjection = infoMap.get(id);
                    BookType bookType = bookCatalogProjection.getBookType();
                    LocalDate loanDate = LocalDate.now();
                    Long loanTerm = loanTermPolicy.loanPeriodFor(bookType);
                    LocalDate dueDate = loanDate.plusDays(loanTerm);

                    return BookLoan.builder()
                            .memberId(command.memberId())
                            .bookId(id)
                            .loanStatus(LoanStatus.LOANED)
                            .bookCategory(bookType)
                            .loanDate(loanDate)
                            .dueDate(dueDate)
                            .returnDate(null)
                            .build();
                })
                .toList();

        // 레파지토리 저장 수행...
        List<BookLoan> savedBookLoans = loanRepository.saveAll(bookLoans);





    }
}
