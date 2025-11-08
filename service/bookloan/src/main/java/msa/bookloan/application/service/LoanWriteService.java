package msa.bookloan.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.projection.entity.BookCatalogProjection;
import msa.bookloan.adapter.out.persistence.projection.repository.BookCatalogProjectionRepository;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.service.dto.LoanCreateResult;
import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.BookType;
import msa.bookloan.domain.policy.LoanTermPolicy;
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
public class LoanWriteService {

    private final BookLoanRepository bookLoanRepository;
    private final BookCatalogProjectionRepository projectionRepository;
    private final LoanTermPolicy loanTermPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final Snowflake snowflake;
    private final Clock clock;

    @Transactional
    public LoanCreateResult createAndPublish(Long memberId, Long bookId) {
        long loanId = snowflake.nextId();
        long sagaId = snowflake.nextId();
        long eventId = snowflake.nextId();

        log.info("[Saga] Saga 생성 시작: loanId={}, sagaId={}, memberId={}, bookId={}",
                loanId, sagaId, memberId, bookId);

        BookType bookType = projectionRepository.findById(bookId)
                .map(BookCatalogProjection::getBookType)
                .map(BookType::from)
                .orElseGet(() -> {
                    log.info("[Saga] BookCatalogProjection을 찾을 수 없음. 'UNKNOWN' 타입으로 진행: bookId={}", bookId);
                    return BookType.UNKNOWN;
                });

        LocalDate loanDate = LocalDate.now(clock);
        LocalDate dueDate = loanDate.plusDays(loanTermPolicy.loanPeriodFor(bookType));

        BookLoan.CreateSpec createSpec = new BookLoan.CreateSpec(
                loanId, memberId, bookId, sagaId, loanDate, dueDate
        );
        BookLoan loan = BookLoan.createPending(createSpec);
        bookLoanRepository.saveAndFlush(loan);
        Long aggregateVersion = loan.getVersion();

        log.info("[Saga] BookLoan 저장 완료. Saga 이벤트 발행: sagaId={}, eventId={}, version={}",
                sagaId, eventId, aggregateVersion);

        eventPublisher.publishEvent(new LoanRequestedInternalEvent(
                sagaId, loanId, memberId, bookId,
                eventId, aggregateVersion , LocalDateTime.now(clock)
        ));

        return new LoanCreateResult(loanId, sagaId);

    }

}
