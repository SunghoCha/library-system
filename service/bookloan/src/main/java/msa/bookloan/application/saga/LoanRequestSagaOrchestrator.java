package msa.bookloan.application.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.common.events.bookloan.saga.command.CheckMemberCommand;
import msa.common.events.bookloan.saga.command.RefundPointCommand;
import msa.common.events.bookloan.saga.command.ReleaseInventoryCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReleasedReply;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReserveFailedReply;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReservedReply;
import msa.common.events.bookloan.saga.reply.member.MemberCheckedReply;
import msa.common.events.bookloan.saga.reply.point.PointChargeFailedReply;
import msa.common.events.bookloan.saga.reply.point.PointChargedReply;
import msa.common.events.bookloan.saga.reply.point.PointRefundedReply;
import msa.common.events.bookloan.saga.reply.shipping.ShippingAcceptedReply;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduleFailedReply;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduledReply;
import msa.bookloan.application.saga.steps.InventoryStepService;
import msa.bookloan.application.saga.steps.MemberStepService;
import msa.bookloan.application.saga.steps.PointStepService;
import msa.bookloan.application.saga.steps.ShippingStepService;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.SagaAbortReason;
import msa.bookloan.domain.saga.SagaStatus;
import msa.common.snowflake.Snowflake;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import static java.time.LocalDateTime.now;
import static msa.bookloan.domain.saga.LoanSagaStep.MEMBER_CHECKING;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRequestSagaOrchestrator {

    private final Clock clock;
    private final Snowflake snowflake;
    private final SagaTimeouts sagaTimeouts;
    private final LoanSagaRepository sagaRepository;
    private final BookLoanRepository bookLoanRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;

    private final MemberStepService memberStepService;
    private final InventoryStepService inventoryStepService;
    private final PointStepService pointStepService;
    private final ShippingStepService shippingStepService;

    // 사가 시작 - 멤버 확인 커맨드만 발행
    @Transactional(propagation = Propagation.MANDATORY)
    public void start(LoanRequestedInternalEvent event) {
        int bound = bookLoanRepository.tryBindSaga(event.loanId(), event.sagaId());
        if (bound == 0) {
            // 이미 바인딩되어 있거나 경합으로 졌음
            log.debug("[Saga] 바인딩 실패(이미 in-flight): loanId={}, sagaId={}", event.loanId(), event.sagaId());
            return;
        }

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
                now(clock).plus(sagaTimeouts.stepTimeout(MEMBER_CHECKING));

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
    public void onMemberChecked(MemberCheckedReply reply) {
        try {
            memberStepService.afterMemberChecked(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 멤버 확인 단계: 중복/경합으로 드롭. sagaId={}, reason={}",
                    reply.sagaId(), ex.getMessage());
        }
    }

    // 재고 예약 성공 리플라이 수신 - 포인트 차징 단계로 전이
    public void onInventoryReserved(InventoryReservedReply reply) {
        try {
            inventoryStepService.afterInventoryReserved(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 재고 예약 단계: 중복/경합으로 드롭. sagaId={}, reason={}",
                    reply.sagaId(), ex.getMessage());
        }
    }

    // 재고 예약 실패 리플라이 수신 - 보상/실패 전이
    public void onInventoryReserveFailed(InventoryReserveFailedReply reply) {
        try {
            inventoryStepService.afterInventoryReserveFailed(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 재고 예약 실패 단계: 중복/경합으로 드롭. sagaId={}, reason={}",
                    reply.sagaId(), ex.getMessage());
        }
    }

    public void onPointCharged(PointChargedReply reply) {
        try {
            pointStepService.afterPointCharged(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 포인트 차징 단계: 중복/경합으로 드롭. sagaId={}, reason={}",
                    reply.sagaId(), ex.getMessage());
        }
    }

    // 포인트 차징 실패 -> 종료(필요 시 포인트/재고 보상은 다음 단계에서)
    public void onPointChargeFailed(PointChargeFailedReply reply) {
        try {
            pointStepService.afterPointChargeFailed(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 포인트 차징 단계: 중복/경합 드롭. sagaId={}, reason={}",
                    reply.sagaId(), ex.getMessage());
        }
    }

    public void onShippingAccepted(ShippingAcceptedReply reply) {
        try {
            shippingStepService.afterShippingAccepted(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 배송 ACK 단계: 중복/경합 드롭. sagaId={}, reason={}", reply.sagaId(), ex.getMessage());
        }
    }

    // 배송 스케줄 성공 -> FINISHED(Pivot 통과)
    public void onShippingScheduled(ShippingScheduledReply reply) {
        try {
            shippingStepService.afterShippingScheduled(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 배송 스케줄 성공: 중복/경합 드롭. sagaId={}, reason={}", reply.sagaId(), ex.getMessage());
        }
    }

    // 배송 스케줄 실패 -> 종료(필요 시 보상 플로우는 별도)
    public void onShippingScheduleFailed(ShippingScheduleFailedReply reply) {
        try {
            shippingStepService.afterShippingScheduleFailed(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 배송 스케줄 실패: 중복/경합 드롭. sagaId={}, reason={}", reply.sagaId(), ex.getMessage());
        }
    }

    // 포인트 환불 성공 -> 인벤토리 해제 커맨드 발행(보상 체인 계속)
    public void onPointRefunded(PointRefundedReply reply) {
        try {
            pointStepService.afterPointRefunded(reply); // 저장안해서 낙관적 락 예외 발생안하는 케이스
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 포인트 환불 보상 단계: 중복/경합 드롭. sagaId={}, reason={}", reply.sagaId(), ex.getMessage());
        }
    }

    // 재고 해제 성공 -> 보상 종료(FAILED 확정)
    public void onInventoryReleased(InventoryReleasedReply reply) {
        try {
            inventoryStepService.afterInventoryReleased(reply);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 보상 종료 단계 중복/경합 드롭. sagaId={}, reason={}", reply.sagaId(), ex.getMessage());
        }
    }

//    @Transactional(propagation = Propagation.MANDATORY)
//    public void requestCancelV1(String sagaId, SagaAbortReason reason, Long causationEventId) {
//        LoanSaga saga = sagaRepository.findById(sagaId).orElseThrow();
//
//        // 피벗 이후 또는 터미널/보상 중이면 취소 불가
//        if (saga.isTerminal() || saga.isAfterPivot() || saga.getStatus() == SagaStatus.COMPENSATING) {
//            log.debug("[Saga] 취소 드롭: sagaId={}, status={}, step={}", sagaId, saga.getStatus(), saga.getCurrentStep());
//            return;
//        }
//
//        Duration to = sagaTimeouts.compensationTimeoutFor();
//
//        switch (saga.getCurrentStep()) {
//            case INIT:
//            case MEMBER_CHECKING:
//            case INVENTORY_RESERVING:
//                // 외부 자원 확정 전 -> 즉시 취소 + 언락
//                if (saga.markCancelled(reason)) {
//                    sagaRepository.save(saga);
//                    bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
//                    log.info("[Saga] 종료(즉시 취소): sagaId={}, step={}, reason={}",
//                            saga.getSagaId(), saga.getCurrentStep(), reason);
//                }
//                return;
//
//            case POINT_CHARGING:
//                // 재고 확보됐을 수 있음 -> 보상 시작(ReleaseInventory)
//                if (saga.enterCompensating(to, now(clock))) {
//                    sagaRepository.save(saga);
//                    commandOutboxRecorder.save(createReleaseInventoryCommand(saga, causationEventId));
//                    log.info("[Saga] 보상 시작(ReleaseInventory): sagaId={}, reason={}", saga.getSagaId(), reason);
//                }
//                return;
//
//            case SHIPPING_SCHEDULING:
//            case SHIPPING_ACCEPTED:
//                // 포인트·재고가 확보됐을 수 있음 -> 보상 시작(RefundPoint)
//                if (saga.enterCompensating(to, now(clock))) {
//                    sagaRepository.save(saga);
//                    commandOutboxRecorder.save(createRefundPointCommand(saga, causationEventId));
//                    log.info("[Saga] 보상 시작(RefundPoint): sagaId={}, reason={}", saga.getSagaId(), reason);
//                }
//                return;
//
//            default:
//                // 방어적 기본값
//                if (saga.markCancelled(reason)) {
//                    sagaRepository.save(saga);
//                    bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
//                    log.info("[Saga] 종료(디폴트 취소): sagaId={}, step={}, reason={}",
//                            saga.getSagaId(), saga.getCurrentStep(), reason);
//                }
//        }
//    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean requestCancel(String sagaId, SagaAbortReason reason, Long causationEventId) {
        LoanSaga saga = sagaRepository.findForUpdate(sagaId)
                .orElseThrow(() -> new SagaNotFoundException(sagaId)); // 취소 상태로 업데이트

        // 피벗 이후/터미널 가드
        if (!saga.markCancelRequested(reason)) return false;

        Duration to = sagaTimeouts.compensationTimeoutFor();

        switch (saga.getCurrentStep()) {
            case INIT, MEMBER_CHECKING, INVENTORY_RESERVING: {
                // 외부자원 아직 확정 전 -> 즉시 취소
                saga.markCancelled(reason);
                sagaRepository.save(saga);
                bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getId());
                return true;
            }
            case POINT_CHARGING: {
                // 재고가 잡혀있을 수 있음 -> 보상 전이 + ReleaseInventory
                saga.enterCompensating(to, LocalDateTime.now(clock));
                sagaRepository.save(saga);
                commandOutboxRecorder.save(createReleaseInventoryCommand(saga, causationEventId));

                return true;
            }
            case SHIPPING_SCHEDULING, SHIPPING_ACCEPTED: {
                // 포인트/재고가 확정됐을 수 있음 -> 보상 전이 + RefundPoint
                saga.enterCompensating(to, LocalDateTime.now(clock));
                sagaRepository.save(saga);
                commandOutboxRecorder.save(createRefundPointCommand(saga, causationEventId));
                return true;
            }
            default: {
                // 방어적 코드 (보상트랜잭션 필요한건데 이게 수행되면 오히려 위험할지도? 예외던지는게 나은가)
                saga.markCancelled(reason);
                sagaRepository.save(saga);
                bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getId());
                return true;
            }
        }

    }

    private CheckMemberCommand createMemberCommand(LoanRequestedInternalEvent event) {
        return CheckMemberCommand.of(
                snowflake.nextId(),
                event.sagaId(),
                event.loanId(),
                event.memberId(),
                event.eventId()
        );
    }

    private ReleaseInventoryCommand createReleaseInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReleaseInventoryCommand.of(
                snowflake.nextId(),
                saga.getId(),
                saga.getLoanId(),
                saga.getBookId(),
                causationEventId
        );
    }

    private RefundPointCommand createRefundPointCommand(LoanSaga saga, Long causationEventId) {
        return RefundPointCommand.of(
                snowflake.nextId(),
                saga.getSagaId(),
                saga.getLoanId(),
                saga.getMemberId(),
                causationEventId
        );
    }

}
