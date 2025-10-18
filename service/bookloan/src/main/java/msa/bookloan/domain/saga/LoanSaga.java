package msa.bookloan.domain.saga;

import jakarta.persistence.*;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanSaga extends BaseTimeEntity {

    @Id
    @Column(name = "saga_id",  nullable = false)
    private String sagaId;  // 스노우플레이크 기반으로 만들고 String으로 변환

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion;         // @Version 값(BookLoan 엔티티)

    @Column(name = "trigger_event_id", nullable = false)
    private Long triggerEventId;           // 사가를 시작시킨 최초 이벤트의 id

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SagaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private LoanSagaStep currentStep;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "worker_id")
    private String workerId;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "step_started_at")
    private LocalDateTime stepStartedAt; // 나중에 운영단계에서 필요할듯 당장은 쓸 일 없어보임

    @Setter
    @Column(name = "step_deadline_at")
    private LocalDateTime stepDeadlineAt; //

    @Builder
    public LoanSaga(String sagaId, Long loanId, Long memberId, Long bookId,
                    Long aggregateVersion, Long triggerEventId, SagaStatus status,
                    LoanSagaStep currentStep, String lastError, String workerId,
                    LocalDateTime leaseUntil, LocalDateTime stepStartedAt,
                    LocalDateTime stepDeadlineAt) {
        this.sagaId = sagaId;
        this.loanId = loanId;
        this.memberId = memberId;
        this.bookId = bookId;
        this.aggregateVersion = aggregateVersion;
        this.triggerEventId = triggerEventId;
        this.status = status;
        this.currentStep = currentStep;
        this.lastError = lastError;
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
        this.stepStartedAt = stepStartedAt;
        this.stepDeadlineAt = stepDeadlineAt;
    }

    public static LoanSaga startNew(String sagaId,
                                    Long loanId,
                                    Long memberId,
                                    Long bookId,
                                    Long aggregateVersion,
                                    Long triggerEventId) {
        LocalDateTime now = LocalDateTime.now();
        return LoanSaga.builder()
                .sagaId(sagaId)
                .loanId(loanId)
                .memberId(memberId)
                .bookId(bookId)
                .aggregateVersion(aggregateVersion)
                .triggerEventId(triggerEventId)
                .status(SagaStatus.PROCESSING)
                .currentStep(LoanSagaStep.INIT)
                .stepStartedAt(now)       // INIT 스텝 시작 시각
                .stepDeadlineAt(null) // 사가 진행하면서 계속 갱신
                .build();
    }

    @Deprecated
    public void markProcessing(LoanSagaStep step) {
        this.currentStep = step;
        this.status = SagaStatus.PROCESSING;
        this.stepStartedAt = LocalDateTime.now();
        this.stepDeadlineAt = null;
    }

    public boolean markProcessing(LoanSagaStep step, Duration timeout) {
        // 터미널/보상 중엔 앞으로 진행 금지
        if (isTerminal() || this.status == SagaStatus.COMPENSATING) {
            return false;
        }

        // 동일 스텝 재진입은 멱등 처리
        if (this.status == SagaStatus.PROCESSING && this.currentStep == step) {
            return false;
        }

        this.currentStep = step;
        this.status = SagaStatus.PROCESSING;
        LocalDateTime now = LocalDateTime.now();
        this.stepStartedAt = now;
        this.stepDeadlineAt = now.plus(timeout);

        return true;
    }

    public boolean markCompleted() {
        // 이미 완료면 멱등, 다른 터미널이면 무시
        if (this.status == SagaStatus.COMPLETED || isTerminal()) {
            return false;
        }

        // 정책상 완료 가능한 지점만 허용(현재 설계: SHIPPING_SCHEDULING 이후)
        if (this.currentStep != LoanSagaStep.SHIPPING_SCHEDULING
                && this.currentStep != LoanSagaStep.SHIPPING_ACCEPTED) {
            return false;
        }

        this.currentStep = LoanSagaStep.FINISHED;
        this.status = SagaStatus.COMPLETED;
        this.stepDeadlineAt = null;
        this.workerId = null;
        this.leaseUntil = null;
        this.lastError = null;

        return true;
    }

    public boolean markFailed(SagaAbortReason reason) {
        // 멱등/우선순위: 완료된 건은 건드리지 않음
        if (this.status == SagaStatus.COMPLETED || this.status == SagaStatus.FAILED) {
            return false;
        }

        this.status = SagaStatus.FAILED;
        this.lastError = reason != null ? reason.name() : SagaAbortReason.UNKNOWN.name(); ;
        this.stepDeadlineAt = null;
        this.workerId = null;
        this.leaseUntil = null;

        return true;
    }

    public boolean markCancelled(SagaAbortReason reason) {
        if (this.status == SagaStatus.COMPLETED || this.status == SagaStatus.CANCELLED) {
            return false;
        }

        this.status = SagaStatus.CANCELLED;
        this.lastError = reason.name();
        this.stepDeadlineAt = null;
        this.workerId = null;
        this.leaseUntil = null;

        return true;
    }

    public boolean  enterCompensating(Duration timeout) {
        // 이미 끝났거나 피벗 통과면 아무 것도 하지 않음 (방어)
        if (isTerminal() || isAfterPivot() || this.status == SagaStatus.COMPENSATING) {
            return false;
        }
        this.status = SagaStatus.COMPENSATING;
        LocalDateTime now = LocalDateTime.now();
        this.stepStartedAt = now;                    // 보상 시작 시각 갱신
        this.stepDeadlineAt = now.plus(timeout);     // 보상 타임아웃
        // 워커 시맨틱락 해제
        this.workerId = null;
        this.leaseUntil = null;

        return true;
    }

    public boolean isProcessingAt(LoanSagaStep step) {
        return this.getStatus() == SagaStatus.PROCESSING && this.getCurrentStep() == step;
    }

    public boolean isProcessingAtAny(LoanSagaStep... steps) {
        if (this.getStatus() != SagaStatus.PROCESSING) return false;
        for (LoanSagaStep s : steps) {
            if (this.getCurrentStep() == s) return true;
        }
        return false;
    }

    public boolean isCompensatingFrom(LoanSagaStep step) {
        return this.getStatus() == SagaStatus.COMPENSATING && this.getCurrentStep() == step;
    }

    public boolean moveCompensatingTo(LoanSagaStep step, Duration timeout) {
        if (this.getStatus() != SagaStatus.COMPENSATING) return false;
        this.setStepDeadlineAt(LocalDateTime.now().plus(timeout));
        this.currentStep = step;
        this.stepStartedAt = LocalDateTime.now();
        return true;
    }

    public boolean isTerminal() {
        return this.status == SagaStatus.COMPLETED
                || this.status == SagaStatus.FAILED
                || this.status == SagaStatus.CANCELLED
                || this.status == SagaStatus.TIMED_OUT;
    }

    public boolean isAfterPivot() {
        // 현 설계: ShippingScheduled가 커밋되면 FINISHED로 전이됨 -> 그 시점이 pivot 통과
        // 지금은 피벗트랜잭션이 마지막인데 나중엔 바뀔수도 있음
        return this.currentStep == LoanSagaStep.FINISHED;
    }

    public boolean canAcceptCancel() {
        return !isTerminal() && !isAfterPivot();
    }
}
