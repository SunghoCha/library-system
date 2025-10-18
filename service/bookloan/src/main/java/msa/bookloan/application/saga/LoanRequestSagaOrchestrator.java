package msa.bookloan.application.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaRepository;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.saga.command.CheckMemberCommand;
import msa.bookloan.application.saga.command.RefundPointCommand;
import msa.bookloan.application.saga.command.ReleaseInventoryCommand;
import msa.bookloan.application.saga.reply.inventory.InventoryReleasedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedInternalEvent;
import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointChargeFailedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointChargedInternalEvent;
import msa.bookloan.application.saga.reply.point.PointRefundedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingAcceptedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
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
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import static msa.bookloan.domain.saga.LoanSagaStep.MEMBER_CHECKING;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRequestSagaOrchestrator {

    private final Clock clock;
    private final Snowflake snowflake;
    private final LoanSagaRepository sagaRepository;
    private final CommandOutboxRecorder commandOutboxRecorder;
    private final SagaTimeouts sagaTimeouts;
    private final BookLoanRepository bookLoanRepository;
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
                LocalDateTime.now(clock).plus(sagaTimeouts.stepTimeout(MEMBER_CHECKING));

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
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onMemberChecked(MemberCheckedInternalEvent event) {
        try {
            memberStepService.afterMemberChecked(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 멤버 확인 단계: 중복/경합으로 드롭. sagaId={}, reason={}",
                    event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 멤버 확인 단계: 예상치 못한 오류. 수동 점검 필요. sagaId={}",
                    event.sagaId(), ex);
        }
    }

    // 재고 예약 성공 리플라이 수신 - 포인트 차징 단계로 전이
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onInventoryReserved(InventoryReservedInternalEvent event) {
        try {
            inventoryStepService.afterInventoryReserved(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 재고 예약 단계: 중복/경합으로 드롭. sagaId={}, reason={}",
                    event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 재고 예약 단계: 예상치 못한 오류. 수동 점검 필요. sagaId={}", event.sagaId(), ex);
        }
    }

    // 재고 예약 실패 리플라이 수신 - 보상/실패 전이
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onInventoryReserveFailed(InventoryReserveFailedInternalEvent event) {
        try {
            inventoryStepService.afterInventoryReserveFailed(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 재고 예약 실패 단계: 중복/경합으로 드롭. sagaId={}, reason={}",
                    event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 재고 예약 실패 단계: 예상치 못한 오류. 수동 점검 필요. sagaId={}",
                    event.sagaId(), ex);
        }
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onPointCharged(PointChargedInternalEvent event) {
        try {
            pointStepService.afterPointCharged(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 포인트 차징 단계: 중복/경합으로 드롭. sagaId={}, reason={}",
                    event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 포인트 차징 단계: 예상치 못한 오류. 수동 점검 필요. sagaId={}",
                    event.sagaId(), ex);
        }
    }

    // 포인트 차징 실패 -> 종료(필요 시 포인트/재고 보상은 다음 단계에서)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onPointChargeFailed(PointChargeFailedInternalEvent event) {
        try {
            pointStepService.afterPointChargeFailed(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 포인트 차징 단계: 중복/경합 드롭. sagaId={}, reason={}",
                    event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 포인트 차징 단계: 예상치 못한 오류. 수동 점검 필요. sagaId={}",
                    event.sagaId(), ex);
        }
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onShippingAccepted(ShippingAcceptedInternalEvent event) {
        try {
            shippingStepService.afterShippingAccepted(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 배송 ACK 단계: 중복/경합 드롭. sagaId={}, reason={}", event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 배송 ACK 단계: 예상치 못한 오류. sagaId={}", event.sagaId(), ex);
        }
    }

    // 배송 스케줄 성공 -> FINISHED(Pivot 통과)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onShippingScheduled(ShippingScheduledInternalEvent event) {
        try {
            shippingStepService.afterShippingScheduled(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 배송 스케줄 성공: 중복/경합 드롭. sagaId={}, reason={}", event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 배송 스케줄 성공: 예상치 못한 오류. sagaId={}", event.sagaId(), ex);
        }
    }

    // 배송 스케줄 실패 -> 종료(필요 시 보상 플로우는 별도)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onShippingScheduleFailed(ShippingScheduleFailedInternalEvent event) {
        try {
            shippingStepService.afterShippingScheduleFailed(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 배송 스케줄 실패: 중복/경합 드롭. sagaId={}, reason={}", event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 배송 스케줄 실패: 예상치 못한 오류. sagaId={}", event.sagaId(), ex);
        }
    }

    // 포인트 환불 성공 -> 인벤토리 해제 커맨드 발행(보상 체인 계속)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onPointRefunded(PointRefundedInternalEvent event) {
        try {
            pointStepService.afterPointRefunded(event); // 저장안해서 낙관적 락 예외 발생안하는 케이스
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 포인트 환불 보상 단계: 중복/경합 드롭. sagaId={}, reason={}", event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 포인트 환불 보상 단계: 예상치 못한 오류. sagaId={}",
                    event.sagaId(), ex);
        }
    }

    // 재고 해제 성공 -> 보상 종료(FAILED 확정)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onInventoryReleased(InventoryReleasedInternalEvent event) {
        try {
            inventoryStepService.afterInventoryReleased(event);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("[Saga] 보상 종료 단계 중복/경합 드롭. sagaId={}, reason={}", event.sagaId(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("[Saga] 보상 종료 단계 처리 오류: sagaId={}", event.sagaId(), ex);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestCancel(String sagaId, SagaAbortReason reason, Long causationEventId) {
        LoanSaga saga = sagaRepository.findById(sagaId).orElseThrow();

        // 피벗 이후 또는 터미널/보상 중이면 취소 불가
        if (saga.isTerminal() || saga.isAfterPivot() || saga.getStatus() == SagaStatus.COMPENSATING) {
            log.debug("[Saga] 취소 드롭: sagaId={}, status={}, step={}", sagaId, saga.getStatus(), saga.getCurrentStep());
            return;
        }

        Duration to = sagaTimeouts.compensationTimeoutFor();

        switch (saga.getCurrentStep()) {
            case INIT:
            case MEMBER_CHECKING:
            case INVENTORY_RESERVING:
                // 외부 자원 확정 전 -> 즉시 취소 + 언락
                if (saga.markCancelled(reason)) {
                    sagaRepository.save(saga);
                    bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
                    log.info("[Saga] 종료(즉시 취소): sagaId={}, step={}, reason={}",
                            saga.getSagaId(), saga.getCurrentStep(), reason);
                }
                return;

            case POINT_CHARGING:
                // 재고 확보됐을 수 있음 -> 보상 시작(ReleaseInventory)
                if (saga.enterCompensating(to)) {
                    sagaRepository.save(saga);
                    commandOutboxRecorder.save(createReleaseInventoryCommand(saga, causationEventId));
                    log.info("[Saga] 보상 시작(ReleaseInventory): sagaId={}, reason={}", saga.getSagaId(), reason);
                }
                return;

            case SHIPPING_SCHEDULING:
            case SHIPPING_ACCEPTED:
                // 포인트·재고가 확보됐을 수 있음 -> 보상 시작(RefundPoint)
                if (saga.enterCompensating(to)) {
                    sagaRepository.save(saga);
                    commandOutboxRecorder.save(createRefundPointCommand(saga, causationEventId));
                    log.info("[Saga] 보상 시작(RefundPoint): sagaId={}, reason={}", saga.getSagaId(), reason);
                }
                return;

            default:
                // 방어적 기본값
                if (saga.markCancelled(reason)) {
                    sagaRepository.save(saga);
                    bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
                    log.info("[Saga] 종료(디폴트 취소): sagaId={}, step={}, reason={}",
                            saga.getSagaId(), saga.getCurrentStep(), reason);
                }
        }
    }

    private CheckMemberCommand createMemberCommand(LoanRequestedInternalEvent event) {
        return CheckMemberCommand.of(
                snowflake.nextId(),
                event.sagaId(),
                event.loanId(),
                event.memberId(),
                event.eventId()             // causation
        );
    }

    private ReleaseInventoryCommand createReleaseInventoryCommand(LoanSaga saga, Long causationEventId) {
        return ReleaseInventoryCommand.of(
                snowflake.nextId(),
                saga.getSagaId(),
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
