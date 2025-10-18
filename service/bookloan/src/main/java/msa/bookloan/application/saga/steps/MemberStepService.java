package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.command.ReserveInventoryCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
import msa.bookloan.domain.saga.LoanSaga;
import msa.common.snowflake.Snowflake;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static msa.bookloan.domain.saga.LoanSagaStep.INVENTORY_RESERVING;
import static msa.bookloan.domain.saga.LoanSagaStep.MEMBER_CHECKING;
import static msa.bookloan.domain.saga.SagaAbortReason.BLACKLISTED;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberStepService {

    private final LoanSagaRepository sagaRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;
    private final SagaTimeouts sagaTimeouts;
    private final BookLoanRepository bookLoanRepository;
    private final Snowflake snowflake;

    @Retryable(
            retryFor = {
                    QueryTimeoutException.class,
                    TransientDataAccessResourceException.class
            },
            noRetryFor = {
                    OptimisticLockingFailureException.class,
                    DataIntegrityViolationException.class
            },
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 800, random = true)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterMemberChecked(MemberCheckedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(MEMBER_CHECKING)) return;

        if (event.payload().blacklisted()) {
            boolean changed = saga.markFailed(BLACKLISTED);
            if (!changed) return;

            // 낙관적 예외 발생시 바로 던지도록 설계
            sagaRepository.saveAndFlush(saga);

            // flush 성공 이후에만 부수효과
            bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
            log.info("[Saga] 블랙리스트로 종료: sagaId={}", event.sagaId());
            return;
        }

        boolean stepped = saga.markProcessing(INVENTORY_RESERVING, sagaTimeouts.stepTimeout(INVENTORY_RESERVING));
        if (!stepped) return;

        sagaRepository.saveAndFlush(saga);
        ReserveInventoryCommand command = createInventoryCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);
        log.info("[Saga] 재고 예약 커맨드 발행 준비: sagaId={}, bookId={}", event.sagaId(), saga.getBookId());

    }

    private ReserveInventoryCommand createInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReserveInventoryCommand.of(
                snowflake.nextId(),
                saga.getSagaId(),
                saga.getLoanId(),
                saga.getBookId(),
                causationEventId
        );
    }

    @Recover
    public void recoverOnTransient(Exception ex, Object event) {
        log.warn("[Saga] 멤버 단계 재시도 소진. event={}, err={}", event, ex.getMessage(), ex);
    }

}

