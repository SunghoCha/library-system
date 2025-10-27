package msa.bookloan.application.saga.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.command.ReserveInventoryCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.member.MemberCheckedReply;
import msa.bookloan.domain.saga.LoanSaga;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

import static java.time.LocalDateTime.now;
import static msa.bookloan.domain.saga.LoanSagaStep.INVENTORY_RESERVING;
import static msa.bookloan.domain.saga.LoanSagaStep.MEMBER_CHECKING;
import static msa.bookloan.domain.saga.SagaAbortReason.BLACKLISTED;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberStepService {

    private final Clock clock;
    private final LoanSagaRepository sagaRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;
    private final SagaTimeouts sagaTimeouts;
    private final BookLoanRepository bookLoanRepository;
    private final Snowflake snowflake;

    @Transactional
    public void afterMemberChecked(MemberCheckedReply event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new SagaNotFoundException(event.sagaId()));

        if (saga.isTerminal()) return;
        if (!saga.isProcessingAt(MEMBER_CHECKING)) return;

        if (event.payload().blacklisted()) {
            boolean changed = saga.markFailed(BLACKLISTED);
            if (!changed) return;

            // 낙관적 예외 발생시 바로 던지도록 설계 (save로 해도 큰 차이는 없을듯?)
            sagaRepository.saveAndFlush(saga);

            bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
            log.info("[Saga] 블랙리스트로 종료: sagaId={}", event.sagaId());
            return;
        }

        boolean stepped = saga.markProcessing(INVENTORY_RESERVING, sagaTimeouts.stepTimeout(INVENTORY_RESERVING), now(clock));
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

}

