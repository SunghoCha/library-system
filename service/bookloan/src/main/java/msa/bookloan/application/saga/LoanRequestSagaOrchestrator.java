package msa.bookloan.application.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaRepository;
import msa.bookloan.application.saga.command.*;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReleasedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointRefundedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointChargeFailedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointChargedInternalEvent;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.common.snowflake.Snowflake;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static msa.bookloan.domain.saga.LoanSagaStep.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRequestSagaOrchestrator {

    private final Snowflake snowflake;
    private final LoanSagaRepository sagaRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;
    private final SagaTimeouts sagaTimeouts;

    // 사가 시작 - 멤버 확인 커맨드만 발행
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(LoanRequestedInternalEvent event) {

        boolean created = startSagaRowIfAbsent(event);
        if (!created) {
            log.debug("[Saga] 이미 시작된 사가입니다. sagaId={}", event.sagaId());
            return;
        }

        // 아웃박스에 멤버커맨드 저장하면 폴링해서 메시지 발행
        commandOutboxRecorder.save(createMemberCommand(event));
        log.info("[Saga] 멤버 확인 커맨드 발행 준비: sagaId={}, memberId={}", event.sagaId(), event.memberId());
    }

    private boolean startSagaRowIfAbsent(LoanRequestedInternalEvent event) {
        LocalDateTime deadline =
                LocalDateTime.now().plus(sagaTimeouts.stepTimeout(MEMBER_CHECKING));

        return sagaRepository.insertIfAbsent(
                event.sagaId(),
                event.loanId(),
                event.memberId(),
                event.bookId(),
                event.aggregateVersion(),
                event.eventId(),
                SagaStatus.PROCESSING.name(),
                MEMBER_CHECKING.name(),
                deadline
        );
    }

    // 멤버 확인 리플라이 수신 - 통과 시 재고 예약 커맨드 발행
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberChecked(MemberCheckedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (saga.isTerminal()) {
            log.debug("[Saga] 터미널 상태 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
            return;
        }

        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == MEMBER_CHECKING)) {
            log.debug("[Saga] 스텝/상태 불일치 드랍: sagaId={}, status={}, step={}",
                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        if (event.payload().blacklisted()) {
            saga.markFailed("BLACKLISTED");
            sagaRepository.save(saga);
            log.info("[Saga] 멤버 블랙리스트 감지 → 사가 종료: sagaId={}", event.sagaId());
            return;
        }

        saga.markProcessing(INVENTORY_RESERVING, sagaTimeouts.stepTimeout(INVENTORY_RESERVING));
        sagaRepository.save(saga);

        ReserveInventoryCommand inventoryCommand = createInventoryCommand(saga, event.eventId());
        commandOutboxRecorder.save(inventoryCommand);
        log.info("[Saga] 재고 예약 커맨드 발행 준비: sagaId={}, bookId={}", event.sagaId(), saga.getBookId());

    }

    // 재고 예약 성공 리플라이 수신 - 사가 완료
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInventoryReserved(InventoryReservedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (saga.isTerminal()) {
            log.debug("[Saga] 터미널 상태 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
            return;
        }

        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == INVENTORY_RESERVING)) {
            log.debug("[Saga] 완료 드랍(스텝/상태 불일치): sagaId={}, status={}, step={}",
                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        saga.markProcessing(POINT_CHARGING, sagaTimeouts.stepTimeout(POINT_CHARGING));
        sagaRepository.save(saga);

        ChargePointCommand command = createChargePointCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);
        log.info("[Saga] 포인트 차징 커맨드 발행 준비: sagaId={}, memberId={}", event.sagaId(), saga.getMemberId());
    }

    // 재고 예약 실패 리플라이 수신 - 보상/실패 전이
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInventoryReserveFailed(InventoryReserveFailedInternalEvent e) {
        LoanSaga saga = sagaRepository.findById(e.sagaId()).orElseThrow();

        if (saga.isTerminal()) { // 취소사가 또는 타임아웃으로 이미 처리됨. 또는 중복된 실패이벤트 발행일 경우 등..
            log.debug("[Saga] 터미널 상태 드랍: sagaId={}, status={}", e.sagaId(), saga.getStatus());
            return;
        }

        if (!(saga.getStatus() == SagaStatus.PROCESSING &&
                saga.getCurrentStep() == LoanSagaStep.INVENTORY_RESERVING)) {
            log.debug("[Saga] 실패 리플라이 드랍(스텝/상태 불일치): sagaId={}, status={}, step={}",
                    e.sagaId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        beginCompensation(saga, e.payload().reasonCode(), e.eventId());
        log.info("[Saga] 재고 예약 실패: sagaId={}, reason={}", e.sagaId(), e.payload().reasonCode());
    }

    // 포인트 차징 성공 -> 배송 스케줄링
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPointCharged(PointChargedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (saga.isTerminal()) {
            log.debug("[Saga] 터미널 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
            return;
        }
        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == POINT_CHARGING)) {
            log.debug("[Saga] 스텝/상태 불일치 드랍: sagaId={}, status={}, step={}",
                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        saga.markProcessing(SHIPPING_SCHEDULING, sagaTimeouts.stepTimeout(SHIPPING_SCHEDULING));
        sagaRepository.save(saga);

        ScheduleShippingCommand command = createScheduleShippingCommand(saga, event.eventId());
        commandOutboxRecorder.save(command);
        log.info("[Saga] 배송 스케줄링 커맨드 발행 준비: sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
    }

    // 포인트 차징 실패 -> 종료(필요 시 포인트/재고 보상은 다음 단계에서)
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPointChargeFailed(PointChargeFailedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (saga.isTerminal()) {
            log.debug("[Saga] 터미널 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
            return;
        }
        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == POINT_CHARGING)) {
            log.debug("[Saga] 실패 리플라이 드랍(스텝/상태 불일치): sagaId={}, status={}, step={}",
                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        beginCompensation(saga, event.payload().reasonCode(), event.eventId());
        log.info("[Saga] 포인트 차징 실패 : 보상 시작(ReleaseInventory): sagaId={}, reason={}",
                event.sagaId(), event.payload().reasonCode());
    }

    // 배송 스케줄 성공 -> FINISHED(Pivot 통과)
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShippingScheduled(ShippingScheduledInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (saga.isTerminal()) {
            log.debug("[Saga] 터미널 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
            return;
        }
        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == SHIPPING_SCHEDULING)) {
            log.debug("[Saga] 스텝/상태 불일치 드랍: sagaId={}, status={}, step={}",
                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        saga.markCompleted(); // FINISHED -> pivot 통과
        sagaRepository.save(saga);
        log.info("[Saga] 완료(ShippingScheduled): sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
    }

    // 배송 스케줄 실패 -> 종료(필요 시 보상 플로우는 별도)
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShippingScheduleFailed(ShippingScheduleFailedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (saga.isTerminal()) {
            log.debug("[Saga] 터미널 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
            return;
        }
        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == SHIPPING_SCHEDULING)) {
            log.debug("[Saga] 실패 리플라이 드랍(스텝/상태 불일치): sagaId={}, status={}, step={}",
                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        beginCompensation(saga, event.payload().reasonCode(), event.eventId());
        log.info("[Saga] 배송 스케줄 실패: 보상 시작(ReleaseInventory): sagaId={}, reason={}",
                event.sagaId(), event.payload().reasonCode());
    }

    // 포인트 환불 성공 -> 인벤토리 해제 커맨드 발행(보상 체인 계속)
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPointRefunded(PointRefundedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (saga.isTerminal()) return;
        // COMPENSATING 중이고, 직전 스텝이 SHIPPING_SCHEDULING에서 온 보상 플로우인지 확인
        if (saga.getStatus() != SagaStatus.COMPENSATING || saga.getCurrentStep() != SHIPPING_SCHEDULING) return;

        // (선택) 보상 단계 타임아웃 갱신이 필요하면 여기서 처리
        // saga.bumpCompensationDeadline(sagaTimeouts.stepTimeout(SHIPPING_SCHEDULING));

        commandOutboxRecorder.save(createReleaseInventoryCommand(saga, event.eventId()));
        log.info("[Saga] 보상 진행: 포인트 환불 완료 → 재고 해제 발행, sagaId={}", event.sagaId());
    }

    // 재고 해제 성공 -> 보상 종료(FAILED 확정)
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInventoryReleased(InventoryReleasedInternalEvent event) {
        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();

        if (saga.isTerminal()) return;
        if (saga.getStatus() != SagaStatus.COMPENSATING) return;
        if (saga.getCurrentStep() != POINT_CHARGING && saga.getCurrentStep() != SHIPPING_SCHEDULING) return;

        saga.markFailed("COMPENSATED");
        sagaRepository.save(saga);
        log.info("[Saga] 보상 종료: 재고 해제 완료 → FAILED 확정, sagaId={}", event.sagaId());
    }

    private void beginCompensation(LoanSaga saga, String reason, Long causationEventId) {
        if (saga.isTerminal()) {
            return;
        }

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setStepDeadlineAt(LocalDateTime.now().plus(sagaTimeouts.compensationTimeoutFor()));
        switch (saga.getCurrentStep()) {
            case INVENTORY_RESERVING:
                saga.markFailed(reason);
                sagaRepository.save(saga);
                return;

            case POINT_CHARGING:
                sagaRepository.save(saga);
                commandOutboxRecorder.save(createReleaseInventoryCommand(saga, causationEventId));
                return;

            case SHIPPING_SCHEDULING:
                sagaRepository.save(saga);
                commandOutboxRecorder.save(createRefundPointCommand(saga, causationEventId));
                return;

            default:
                saga.markFailed(reason);
                sagaRepository.save(saga);
        }
    }

    private ReserveInventoryCommand createInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReserveInventoryCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .bookId(saga.getBookId())
                .sourceAggregateVersion(saga.getAggregateVersion()) // BookLoan의 버전
                .causationEventId(causationEventId) // 직전 내부 이벤트 ID
                .build();
    }

    private CheckMemberCommand createMemberCommand(LoanRequestedInternalEvent event) {
        return CheckMemberCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(event.sagaId())
                .loanId(event.loanId())
                .memberId(event.memberId())
                .sourceAggregateVersion(event.aggregateVersion()) // BookLoan의 버전
                .causationEventId(event.eventId()) // 직전 내부 이벤트 ID
                .build();
    }

    private ChargePointCommand createChargePointCommand(LoanSaga saga, Long causationEventId) {
        return ChargePointCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .memberId(saga.getMemberId())
                .sourceAggregateVersion(saga.getAggregateVersion()) // BookLoan의 버전
                .causationEventId(causationEventId) // 직전 내부 이벤트 ID
                .build();
    }

    private ScheduleShippingCommand createScheduleShippingCommand(LoanSaga saga, Long causationEventId) {
        return ScheduleShippingCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .bookId(saga.getBookId())
                .sourceAggregateVersion(saga.getAggregateVersion()) // BookLoan의 버전
                .causationEventId(causationEventId) // 직전 내부 이벤트 ID
                .build();
    }

    private RefundPointCommand createRefundPointCommand(LoanSaga saga, Long causationEventId) {
        return RefundPointCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .memberId(saga.getMemberId())
                .sourceAggregateVersion(saga.getAggregateVersion()) // BookLoan의 버전
                .causationEventId(causationEventId)                 // 직전 내부 이벤트 ID
                .build();
    }

    private ReleaseInventoryCommand createReleaseInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReleaseInventoryCommand.builder()
                .commandId(snowflake.nextId())
                .sagaId(saga.getSagaId())
                .loanId(saga.getLoanId())
                .bookId(saga.getBookId())
                .sourceAggregateVersion(saga.getAggregateVersion()) // BookLoan의 버전
                .causationEventId(causationEventId)                 // 직전 내부 이벤트 ID
                .build();
    }
}
