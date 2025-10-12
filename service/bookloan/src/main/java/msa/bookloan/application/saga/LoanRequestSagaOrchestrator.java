package msa.bookloan.application.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaRepository;
import msa.bookloan.application.saga.command.CheckMemberCommand;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.saga.command.ReserveInventoryCommand;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedInternalEvent;
import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.common.snowflake.Snowflake;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRequestSagaOrchestrator {

    private final Snowflake snowflake;
    private final LoanSagaRepository sagaRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;

    // 사가 시작 - 멤버 확인 커맨드만 발행
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(LoanRequestedInternalEvent event) {

        boolean created = startSagaRowIfAbsent(event);
        if (!created) {
            log.debug("[Saga] 이미 시작된 사가입니다. sagaId={}", event.sagaId());
            return;
        }

        // 아웃박스에 멤버커맨드 저장하면 폴링해서 메시지 발행
        CheckMemberCommand memberCommand = createMemberCommand(event);
        commandOutboxRecorder.save(memberCommand);
        log.info("[Saga] 멤버 확인 커맨드 발행 준비: sagaId={}, memberId={}", event.sagaId(), event.memberId());

    }

    private boolean startSagaRowIfAbsent(LoanRequestedInternalEvent event) {
        LocalDateTime deadline = LocalDateTime.now().plus(SagaTimeouts.STEP_TIMEOUT);

        return sagaRepository.insertIfAbsent(
                event.sagaId(),
                event.loanId(),
                event.memberId(),
                event.bookId(),
                event.aggregateVersion(),
                event.eventId(),
                SagaStatus.PROCESSING.name(),
                LoanSagaStep.MEMBER_CHECKING.name(),
                deadline
        );
    }

    // 멤버 확인 리플라이 수신 - 통과 시 재고 예약 커맨드 발행
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberChecked(MemberCheckedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (event.payload().blacklisted()) {
            saga.markFailed("BLACKLISTED");
            sagaRepository.save(saga);
            log.info("[Saga] 멤버 블랙리스트 감지 → 사가 종료: sagaId={}", event.sagaId());
            return;
        }

        saga.markProcessing(LoanSagaStep.INVENTORY_RESERVING);
        sagaRepository.save(saga);

        ReserveInventoryCommand inventoryCommand = createInventoryCommand(saga, event.eventId());
        commandOutboxRecorder.save(inventoryCommand);
        log.info("[Saga] 재고 예약 커맨드 발행 준비: sagaId={}, bookId={}", event.sagaId(), saga.getBookId());

    }

    // 3) 재고 예약 성공 리플라이 수신 - 사가 완료
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInventoryReserved(InventoryReservedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
        saga.markCompleted();
        sagaRepository.save(saga);
        log.info("[Saga] 완료: sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
    }

    // 4) 재고 예약 실패 리플라이 수신 - 보상/실패 전이
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInventoryReserveFailed(InventoryReserveFailedInternalEvent e) {
        LoanSaga saga = sagaRepository.findById(e.sagaId()).orElseThrow();
        // 필요 시 보상 커맨드 발행(결제/포인트가 있었다면 환불/회수)
        saga.markFailed(e.payload().reasonCode());
        sagaRepository.save(saga);
        log.info("[Saga] 재고 예약 실패: sagaId={}, reason={}", e.sagaId(), e.payload().reasonCode());
    }


    private ReserveInventoryCommand createInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReserveInventoryCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .bookId(saga.getBookId())
                .sourceAggregateVersion(saga.getAggregateVersion()) // 출처: BookLoan.@Version
                .causationEventId(causationEventId)                 // 직전 내부 이벤트 ID
                .build();
    }

    private CheckMemberCommand createMemberCommand(LoanRequestedInternalEvent event) {
        return CheckMemberCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(event.sagaId())
                .loanId(event.loanId())
                .memberId(event.memberId())
                .sourceAggregateVersion(event.aggregateVersion())
                .causationEventId(event.eventId())
                .build();
    }
}
