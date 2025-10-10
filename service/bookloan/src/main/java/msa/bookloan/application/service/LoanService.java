package msa.bookloan.application.service;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.service.dto.LoanCreateResult;
import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanProcessStatus;
import msa.bookloan.domain.policy.LoanTermPolicy;
import msa.bookloan.domain.policy.rule.LoanValidationRule;
import msa.bookloan.adapter.out.persistence.projection.BookCatalogProjectionRepository;
import msa.bookloan.application.port.out.lock.DistributedLock;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.in.web.controller.dto.request.LoanCreateRequest;
import msa.common.snowflake.Snowflake;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class LoanService {

    private final DistributedLock distributedLock;
    private final List<LoanValidationRule> rules;
    private final LoanTermPolicy loanTermPolicy;
    private final BookCatalogProjectionRepository bookCatalogProjectionRepository;
    private final BookLoanRepository bookLoanRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    private final Snowflake snowflake;

    public LoanCreateResult createLoan(Long memberId, LoanCreateRequest request) {
        // 유효성 검사, null 체크 등..

        // 로컬트랜잭션에서 수행할 수 있는 검증로직만 수행
        // ...

        long loanId = snowflake.nextId();
        BookLoan loan = BookLoan.createNew(loanId, memberId, request.bookId());
        bookLoanRepository.save(loan);

        String sagaId = Long.toString(loanId);
        long eventId = snowflake.nextId();

        eventPublisher.publishEvent(new LoanRequestedInternalEvent(
                sagaId, loanId,
                memberId, request.bookId(),
                eventId, LocalDateTime.now(clock)
        ));

        // 수행 후 레포지토리 저장하고 빠르게 반환해서 응답
        return new LoanCreateResult(loanId, sagaId, LoanProcessStatus.RECEIVED);

    }


//    public void createLoan2(LoanCreateRequest request) {
//        // 검증용 컨텍스트 객체
//        LoanContext context = LoanContext.builder()
//                .memberId(command.memberId())
//                .memberGrade(command.memberGrade())
//                .bookIds(command.bookIds())
//                .build();
//
//        rules.forEach(rule -> rule.validate(context));
//
//        Map<Long, BookCatalogProjection> infoMap = bookCatalogProjectionRepository.findAllById(command.bookIds())
//                .stream()
//                .collect(Collectors.toMap(BookCatalogProjection::getBookId, Function.identity()));
//
//
//        List<BookLoan> bookLoans = command.bookIds().stream()
//                .map(id -> {
//                    BookCatalogProjection bookCatalogProjection = infoMap.get(id);
//                    BookType bookType = bookCatalogProjection.getBookType();
//                    LocalDate loanDate = LocalDate.now();
//                    Long loanTerm = loanTermPolicy.loanPeriodFor(bookType);
//                    LocalDate dueDate = loanDate.plusDays(loanTerm);
//
//                    return BookLoan.builder()
//                            .memberId(command.memberId())
//                            .bookId(id)
//                            .loanStatus(LoanStatus.LOANED)
//                            .bookCategory(bookType)
//                            .loanDate(loanDate)
//                            .dueDate(dueDate)
//                            .returnDate(null)
//                            .build();
//                })
//                .toList();
//
//        // 레파지토리 저장 수행...
//        List<BookLoan> savedBookLoans = loanRepository.saveAll(bookLoans);
//
//
//
//
//
//    }
}
