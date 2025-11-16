package msa.bookloan.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.web.controller.dto.request.LoanCancelResult;
import msa.bookloan.adapter.in.web.controller.dto.request.LoanCreateRequest;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.projection.repository.BookCatalogProjectionRepository;
import msa.bookloan.application.event.LoanCancelRequestedInternalEvent;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.application.port.out.lock.DistributedLock;
import msa.bookloan.application.service.dto.LoanCreateResult;
import msa.bookloan.application.service.exception.LoanLimitExceededException;
import msa.bookloan.application.service.exception.LoanNotCancellableException;
import msa.bookloan.domain.exception.BookLoanNotFoundException;
import msa.bookloan.domain.exception.OverdueBlockedException;
import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.policy.LoanLimitPolicy;
import msa.bookloan.domain.saga.SagaAbortReason;
import msa.common.domain.model.MemberGrade;
import msa.common.snowflake.Snowflake;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final DistributedLock distributedLock;
    private final BookCatalogProjectionRepository bookCatalogProjectionRepository;
    private final BookLoanRepository bookLoanRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LoanLimitPolicy loanLimitPolicy;
    private final LoanWriteService loanWriteService;
    private final MemberPort memberPort;
    private final Clock clock;

    private final Snowflake snowflake;

    // 외부 통신 있어서 논트랜잭션
    public LoanCreateResult createLoan(Long memberId, LoanCreateRequest request) {
        log.info("[LoanService] 대출 요청 수신: memberId={}, bookId={}", memberId, request.bookId());
        // 연체 여부 체크
        long overdueCount = bookLoanRepository.countOverdue(memberId, LocalDate.now(clock));
        if (overdueCount > 0) {
            throw new OverdueBlockedException(memberId, overdueCount);
        }

        // 대출한도 체크
        MemberGrade grade = memberPort.getGrade(memberId); // 외부 동기 호출
        int maxLoanCount = loanLimitPolicy.maxLoansFor(grade);
        long currentLoanCount = bookLoanRepository.countActiveByMember(memberId);

        log.info("[LoanService] 대출 한도 체크: memberId={}, grade={}, currentLoans={}, maxLoans={}",
                memberId, grade, currentLoanCount, maxLoanCount);

        if (currentLoanCount >= maxLoanCount) {
            log.info("[LoanService] 대출 거부 (한도 초과): memberId={}, currentLoans={}, maxLoans={}",
                    memberId, currentLoanCount, maxLoanCount);
            throw new LoanLimitExceededException(memberId);
        }

        log.info("[LoanService] 검증 통과. Saga 시작 위임: memberId={}, bookId={}", memberId, request.bookId());
        return loanWriteService.createAndPublish(memberId, request.bookId());

    }


    // TODO : 수정해야하는 로직
    @Transactional
    public LoanCancelResult requestCancel(Long memberId, Long loanId) {
        BookLoan loan = bookLoanRepository.findById(loanId)
                .orElseThrow(() -> new BookLoanNotFoundException(loanId));

        if (!loan.isCancellableBy(memberId)) {
            throw new LoanNotCancellableException(loanId);
        }

        Long sagaId  = loan.getCurrentSagaId();
        if (sagaId  == null) {
            sagaId  = snowflake.nextId();
            loan.attachSaga(sagaId);
        }

        eventPublisher.publishEvent(newUserCancelEvent(loan, memberId, sagaId));
        return new LoanCancelResult(loanId, sagaId , LoanStatus.PENDING);
    }

    private LoanCancelRequestedInternalEvent newUserCancelEvent(BookLoan loan, Long memberId, Long currentSagaId) {
        return new LoanCancelRequestedInternalEvent(
                snowflake.nextId(),
                currentSagaId,
                loan.getId(),
                memberId,
                loan.getBookId(),
                loan.getVersion(),
                SagaAbortReason.USER_CANCEL,   // 사용자 취소는 고정
                LocalDateTime.now(clock)
        );
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
