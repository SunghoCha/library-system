//package msa.bookloan.application.saga;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
//import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
//import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
//import msa.bookloan.application.event.LoanRequestedInternalEvent;
//import msa.bookloan.application.saga.command.*;
//import msa.bookloan.application.saga.reply.inventory.InventoryReleasedInternalEvent;
//import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
//import msa.bookloan.application.saga.reply.inventory.InventoryReservedInternalEvent;
//import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
//import msa.bookloan.application.saga.reply.point.PointChargeFailedInternalEvent;
//import msa.bookloan.application.saga.reply.point.PointChargedInternalEvent;
//import msa.bookloan.application.saga.reply.point.PointRefundedInternalEvent;
//import msa.bookloan.application.saga.reply.shipping.ShippingAcceptedInternalEvent;
//import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedInternalEvent;
//import msa.bookloan.application.saga.reply.shipping.ShippingScheduledInternalEvent;
//import msa.bookloan.domain.saga.LoanSaga;
//import msa.bookloan.domain.saga.LoanSagaStep;
//import msa.bookloan.domain.saga.SagaAbortReason;
//import msa.bookloan.domain.saga.SagaStatus;
//import msa.common.snowflake.Snowflake;
//import org.springframework.context.event.EventListener;
//import org.springframework.dao.OptimisticLockingFailureException;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Duration;
//import java.time.LocalDateTime;
//
//import static msa.bookloan.domain.saga.LoanSagaStep.*;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class LoanRequestSagaOrchestratorV2 {
//
//    private final Snowflake snowflake;
//    private final LoanSagaRepository sagaRepository;
//    private final CommandOutboxRecorder commandOutboxRecorder;
//    private final SagaTimeouts sagaTimeouts;
//    private final BookLoanRepository bookLoanRepository;
//
//    // 사가 시작 - 멤버 확인 커맨드만 발행
//    @Transactional(propagation = Propagation.MANDATORY)
//    public void start(LoanRequestedInternalEvent event) {
//        int bound = bookLoanRepository.tryBindSaga(event.loanId(), event.sagaId());
//        if (bound == 0) {
//            // 이미 바인딩되어 있거나 경합으로 졌음
//            log.debug("[Saga] 바인딩 실패(이미 in-flight): loanId={}, sagaId={}", event.loanId(), event.sagaId());
//            return;
//        }
//
//        boolean created = startSagaRowIfAbsent(event);
//        if (!created) {
//            log.debug("[Saga] 이미 시작된 사가입니다. sagaId={}", event.sagaId());
//            return;
//        }
//
//        // 아웃박스에 멤버커맨드 저장하면 폴링해서 메시지 발행
//        commandOutboxRecorder.save(createMemberCommand(event));
//        log.info("[Saga] 멤버 확인 커맨드 발행 준비: sagaId={}, memberId={}", event.sagaId(), event.memberId());
//    }
//
//    private boolean startSagaRowIfAbsent(LoanRequestedInternalEvent event) {
//        LocalDateTime deadline =
//                LocalDateTime.now().plus(sagaTimeouts.stepTimeout(MEMBER_CHECKING));
//
//        return sagaRepository.insertIfAbsent(
//                event.sagaId(),
//                event.loanId(),
//                event.memberId(),
//                event.bookId(),
//                event.aggregateVersion(),
//                event.eventId(),
//                SagaStatus.PROCESSING.name(),
//                MEMBER_CHECKING.name(),
//                deadline
//        );
//    }
//
//    // 멤버 확인 리플라이 수신 - 통과 시 재고 예약 커맨드 발행
//    @EventListener
//    @Transactional
//    public void onMemberChecked(MemberCheckedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//
//        if (saga.isTerminal()) {
//            log.debug("[Saga] 터미널 상태 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
//            return;
//        }
//
//        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == MEMBER_CHECKING)) {
//            log.debug("[Saga] 스텝/상태 불일치 드랍: sagaId={}, status={}, step={}",
//                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
//            return;
//        }
//
//        if (event.payload().blacklisted() && saga.markFailed(SagaAbortReason.BLACKLISTED)) {
//            sagaRepository.save(saga);
//            log.info("[Saga] 멤버 블랙리스트 감지 -> 사가 종료: sagaId={}", event.sagaId());
//            unlockLoanIfHeld(saga);
//            return;
//        }
//
//        saga.markProcessing(INVENTORY_RESERVING, sagaTimeouts.stepTimeout(INVENTORY_RESERVING));
//        sagaRepository.save(saga);
//
//        ReserveInventoryCommand inventoryCommand = createInventoryCommand(saga, event.eventId());
//        commandOutboxRecorder.save(inventoryCommand);
//        log.info("[Saga] 재고 예약 커맨드 발행 준비: sagaId={}, bookId={}", event.sagaId(), saga.getBookId());
//
//    }
//
//    // 재고 예약 성공 리플라이 수신 - 포인트 차징 단계로 전이
//    @EventListener
//    @Transactional
//    public void onInventoryReserved(InventoryReservedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//
//        if (saga.isTerminal()) {
//            log.debug("[Saga] 터미널 상태 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
//            return;
//        }
//
//        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == INVENTORY_RESERVING)) {
//            log.debug("[Saga] 완료 드랍(스텝/상태 불일치): sagaId={}, status={}, step={}",
//                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
//            return;
//        }
//
//        saga.markProcessing(POINT_CHARGING, sagaTimeouts.stepTimeout(POINT_CHARGING));
//        sagaRepository.save(saga);
//
//        ChargePointCommand command = createChargePointCommand(saga, event.eventId());
//        commandOutboxRecorder.save(command);
//        log.info("[Saga] 포인트 차징 커맨드 발행 준비: sagaId={}, memberId={}", event.sagaId(), saga.getMemberId());
//    }
//
//    // 재고 예약 실패 리플라이 수신 - 보상/실패 전이
//    @EventListener
//    @Transactional
//    public void onInventoryReserveFailed(InventoryReserveFailedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//
//        // 취소사가 또는 타임아웃으로 이미 처리됨. 또는 중복된 실패이벤트 발행일 경우 등..
//        if (saga.isTerminal()) return;
//
//        if (saga.getStatus() != SagaStatus.PROCESSING ||
//                saga.getCurrentStep() != LoanSagaStep.INVENTORY_RESERVING) {
//            return;
//        }
//
//        boolean transitioned = tryFailAndUnlock(saga, SagaAbortReason.INVENTORY_RESERVE_FAILED);
//        if (!transitioned) return;
//
//        log.info("[Saga] 재고 예약 실패 처리 완료: sagaId={}, loanId={}, causeEventId={}, reason={}",
//                event.sagaId(), saga.getLoanId(), event.eventId(), event.payload().reasonCode());
//    }
//
//    private boolean tryFailAndUnlock(LoanSaga saga, SagaAbortReason reason) {
//        if (saga.markFailed(reason)) {
//            sagaRepository.save(saga);
//            unlockLoanIfHeld(saga);
//            return true;
//        }
//        return false;
//    }
//
//    @EventListener
//    @Transactional
//    public void onPointCharged(PointChargedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//
//        if (saga.isTerminal()) {
//            log.debug("[Saga] 터미널 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
//            return;
//        }
//        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == POINT_CHARGING)) {
//            log.debug("[Saga] 스텝/상태 불일치 드랍: sagaId={}, status={}, step={}",
//                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
//            return;
//        }
//
//        saga.markProcessing(SHIPPING_SCHEDULING, sagaTimeouts.stepTimeout(SHIPPING_SCHEDULING));
//        sagaRepository.save(saga);
//
//        ScheduleShippingCommand command = createScheduleShippingCommand(saga, event.eventId());
//        commandOutboxRecorder.save(command);
//        log.info("[Saga] 배송 스케줄링 커맨드 발행 준비: sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
//    }
//
//    // 포인트 차징 실패 -> 종료(필요 시 포인트/재고 보상은 다음 단계에서)
//    @EventListener
//    @Transactional
//    public void onPointChargeFailed(PointChargeFailedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//
//        if (saga.isTerminal()) {
//            log.debug("[Saga] 터미널 드랍: sagaId={}, status={}", event.sagaId(), saga.getStatus());
//            return;
//        }
//        if (!(saga.getStatus() == SagaStatus.PROCESSING && saga.getCurrentStep() == POINT_CHARGING)) {
//            log.debug("[Saga] 실패 리플라이 드랍(스텝/상태 불일치): sagaId={}, status={}, step={}",
//                    event.sagaId(), saga.getStatus(), saga.getCurrentStep());
//            return;
//        }
//
//        boolean transitioned = tryStartCompReleaseInventory(saga, event.eventId());
//        if (!transitioned) return;
//        log.info("[Saga] 포인트 차징 실패 : 보상 시작(ReleaseInventory): sagaId={}, reason={}",
//                event.sagaId(), event.payload().reasonCode());
//    }
//
//    private boolean tryStartCompReleaseInventory(LoanSaga saga, Long causationEventId) {
//        Duration to = sagaTimeouts.compensationTimeoutFor();
//        if (saga.enterCompensating(to)) {
//            sagaRepository.save(saga);
//            commandOutboxRecorder.save(createReleaseInventoryCommand(saga, causationEventId));
//            return true;
//        }
//        return false;
//    }
//
//    @EventListener
//    @Transactional
//    public void onShippingAccepted(ShippingAcceptedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//        if (saga.isTerminal()) return;
//        if (saga.getStatus() != SagaStatus.PROCESSING) return;
//        if (saga.getCurrentStep() != SHIPPING_SCHEDULING) return; // 아직 요청 후 대기 상태여야 함
//
//        // 대기 스텝으로 전이 + 데드라인 갱신(별도 타임아웃 권장)
//        saga.markProcessing(LoanSagaStep.SHIPPING_ACCEPTED,
//                sagaTimeouts.stepTimeout(LoanSagaStep.SHIPPING_ACCEPTED));
//        sagaRepository.save(saga);
//        log.info("[Saga] 배송 접수(Ack): sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
//    }
//
//    // 배송 스케줄 성공 -> FINISHED(Pivot 통과)
//    @EventListener
//    @Transactional
//    public void onShippingScheduled(ShippingScheduledInternalEvent event) {
//        // TODO : 사가 커스텀 예외 구현하기
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow(() -> new IllegalStateException("Saga not found: " + event.sagaId()));
//
//        if (saga.isTerminal() || saga.getStatus() != SagaStatus.PROCESSING) return;
//        if ((saga.getCurrentStep() != SHIPPING_SCHEDULING && saga.getCurrentStep() != SHIPPING_ACCEPTED)) {
//            return;
//        }
//
//        boolean transitioned = saga.markCompleted(); // FINISHED -> pivot 통과
//        if (!transitioned) return;
//
//        try {
//            sagaRepository.save(saga);
//            unlockLoanIfHeld(saga);
//            log.info("[Saga] 완료(ShippingScheduled): sagaId={}, loanId={}", event.sagaId(), saga.getLoanId());
//        } catch (OptimisticLockingFailureException ex) {
//            log.debug("[Saga] stale completion drop: sagaId={}, reason={}", event.sagaId(), ex.getMessage());
//        }
//    }
//
//    // 배송 스케줄 실패 -> 종료(필요 시 보상 플로우는 별도)
//    @EventListener
//    @Transactional
//    public void onShippingScheduleFailed(ShippingScheduleFailedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//
//        if (saga.isTerminal() || saga.getStatus() != SagaStatus.PROCESSING) return;
//        if (saga.getCurrentStep() != SHIPPING_SCHEDULING
//                && saga.getCurrentStep() != SHIPPING_ACCEPTED) return;
//
//        boolean transitioned = tryStartCompRefundPoint(saga, event.eventId());
//        if (!transitioned) return;
//        log.info("[Saga] 배송 스케줄 실패: 보상 시작(RefundPoint): sagaId={}, reason={}",
//                event.sagaId(), event.payload().reasonCode());
//    }
//
//    private boolean tryStartCompRefundPoint(LoanSaga saga, Long causationEventId) {
//        Duration to = sagaTimeouts.compensationTimeoutFor();
//        if (saga.enterCompensating(to)) {
//            sagaRepository.save(saga);
//            commandOutboxRecorder.save(createRefundPointCommand(saga, causationEventId));
//            return true;
//        }
//        return false;
//    }
//
//    // 포인트 환불 성공 -> 인벤토리 해제 커맨드 발행(보상 체인 계속)
//    @EventListener
//    @Transactional
//    public void onPointRefunded(PointRefundedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//
//        if (saga.isTerminal()) return;
//        // COMPENSATING 중이고, 직전 스텝이 SHIPPING_SCHEDULING에서 온 보상 플로우인지 확인
//        if (saga.getStatus() != SagaStatus.COMPENSATING || saga.getCurrentStep() != SHIPPING_SCHEDULING) return;
//
//        // (선택) 보상 단계 타임아웃 갱신이 필요하면 여기서 처리
//        // saga.bumpCompensationDeadline(sagaTimeouts.stepTimeout(SHIPPING_SCHEDULING));
//
//        commandOutboxRecorder.save(createReleaseInventoryCommand(saga, event.eventId()));
//        log.info("[Saga] 보상 진행: 포인트 환불 완료 → 재고 해제 발행, sagaId={}", event.sagaId());
//    }
//
//    // 재고 해제 성공 -> 보상 종료(FAILED 확정)
//    @EventListener
//    @Transactional
//    public void onInventoryReleased(InventoryReleasedInternalEvent event) {
//        LoanSaga saga = sagaRepository.findById(event.sagaId()).orElseThrow();
//
//        if (saga.isTerminal()) return;
//        if (saga.getStatus() != SagaStatus.COMPENSATING) return;
//        if (saga.getCurrentStep() != POINT_CHARGING && saga.getCurrentStep() != SHIPPING_SCHEDULING) return;
//
//        saga.markFailed(SagaAbortReason.COMPENSATION);
//        sagaRepository.save(saga);
//        unlockLoanIfHeld(saga);
//        log.info("[Saga] 보상 종료: 재고 해제 완료 → FAILED 확정, sagaId={}", event.sagaId());
//    }
//
//    @Transactional(propagation = Propagation.MANDATORY)
//    public void requestCancel(String sagaId, SagaAbortReason reason, Long causationEventId) {
//        LoanSaga saga = sagaRepository.findById(sagaId).orElseThrow();// TODO: 사가 커스텀 예외 추가
//
//        if (saga.isTerminal() || saga.isAfterPivot()) {
//            // 피벗 이후엔 취소/보상 불가
//            log.debug("[Saga] 취소/보상 드랍(피벗 이후): sagaId={}, step={}", saga.getSagaId(), saga.getCurrentStep());
//            return;
//        }
//        if (saga.getStatus() == SagaStatus.COMPENSATING) {
//            log.debug("[Saga] 이미 보상 중 드랍: sagaId={}, step={}", saga.getSagaId(), saga.getCurrentStep());
//            return;
//        }
//
//        Duration compensationTimeout = sagaTimeouts.compensationTimeoutFor();
//        // 단계별 정책 (스텝 늘어나면 리팩토링 고려)
//        switch (saga.getCurrentStep()) {
//            case INIT:
//            case MEMBER_CHECKING:
//            case INVENTORY_RESERVING:
//                // 외부 자원 확정 전(또는 확정 불명확) -> 즉시 종료
//                if (saga.markCancelled(reason)) {
//                    sagaRepository.save(saga);
//                    unlockLoanIfHeld(saga);
//                    log.info("[Saga] 종료(즉시 취소): sagaId={}, step={}, reason={}",
//                            saga.getSagaId(), saga.getCurrentStep(), reason);
//                }
//                return;
//
//            case POINT_CHARGING:
//                // 재고는 확보됐을 수 있음 -> 보상 시작(재고 해제)
//                if (saga.enterCompensating(compensationTimeout)) {
//                    sagaRepository.save(saga);
//                    commandOutboxRecorder.save(createReleaseInventoryCommand(saga, causationEventId));
//                    log.info("[Saga] 보상 시작(ReleaseInventory): sagaId={}, reason={}", saga.getSagaId(), reason);
//                }
//                return;
//
//            case SHIPPING_SCHEDULING:
//                // 포인트·재고가 확보됐을 수 있음 -> 보상 시작(포인트 환불)
//                if (saga.enterCompensating(compensationTimeout)) {
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
//                    unlockLoanIfHeld(saga);
//                    log.info("[Saga] 종료(디폴트 취소): sagaId={}, step={}, reason={}",
//                            saga.getSagaId(), saga.getCurrentStep(), reason);
//                }
//        }
//    }
//
//    private void unlockLoanIfHeld(LoanSaga saga) {
//        int cleared = bookLoanRepository.clearSagaIfMatches(saga.getLoanId(), saga.getSagaId());
//        log.debug("[Saga] unlock {}", cleared == 1 ? "ok" : "skip");
//    }
//
//    private ReserveInventoryCommand createInventoryCommand(LoanSaga saga, Long causationEventId) {
//        return ReserveInventoryCommand.builder()
//                .commandId(snowflake.nextId())
//                .sagaId(saga.getSagaId())
//                .loanId(saga.getLoanId())
//                .bookId(saga.getBookId())
//                .loanVersion(saga.getAggregateVersion()) // BookLoan의 버전
//                .causationEventId(causationEventId) // 직전 내부 이벤트 ID
//                .build();
//    }
//
//    private CheckMemberCommand createMemberCommand(LoanRequestedInternalEvent event) {
//        return CheckMemberCommand.builder()
//                .commandId(snowflake.nextId())
//                .sagaId(event.sagaId())
//                .loanId(event.loanId())
//                .memberId(event.memberId())
//                .loanVersion(event.aggregateVersion()) // BookLoan의 버전
//                .causationEventId(event.eventId()) // 직전 내부 이벤트 ID
//                .build();
//    }
//
//    private ChargePointCommand createChargePointCommand(LoanSaga saga, Long causationEventId) {
//        return ChargePointCommand.builder()
//                .commandId(snowflake.nextId())
//                .sagaId(saga.getSagaId())
//                .loanId(saga.getLoanId())
//                .memberId(saga.getMemberId())
//                .loanVersion(saga.getAggregateVersion()) // BookLoan의 버전
//                .causationEventId(causationEventId) // 직전 내부 이벤트 ID
//                .build();
//    }
//
//    private ScheduleShippingCommand createScheduleShippingCommand(LoanSaga saga, Long causationEventId) {
//        return ScheduleShippingCommand.builder()
//                .commandId(snowflake.nextId())
//                .sagaId(saga.getSagaId())
//                .loanId(saga.getLoanId())
//                .bookId(saga.getBookId())
//                .loanVersion(saga.getAggregateVersion()) // BookLoan의 버전
//                .causationEventId(causationEventId) // 직전 내부 이벤트 ID
//                .build();
//    }
//
//    private RefundPointCommand createRefundPointCommand(LoanSaga saga, Long causationEventId) {
//        return RefundPointCommand.builder()
//                .commandId(snowflake.nextId())
//                .sagaId(saga.getSagaId())
//                .loanId(saga.getLoanId())
//                .memberId(saga.getMemberId())
//                .loanVersion(saga.getAggregateVersion()) // BookLoan의 버전
//                .causationEventId(causationEventId)                 // 직전 내부 이벤트 ID
//                .build();
//    }
//
//    private ReleaseInventoryCommand createReleaseInventoryCommand(LoanSaga saga, Long causationEventId) {
//        return ReleaseInventoryCommand.builder()
//                .commandId(snowflake.nextId())
//                .sagaId(saga.getSagaId())
//                .loanId(saga.getLoanId())
//                .bookId(saga.getBookId())
//                .loanVersion(saga.getAggregateVersion()) // BookLoan의 버전
//                .causationEventId(causationEventId)                 // 직전 내부 이벤트 ID
//                .build();
//    }
//}
