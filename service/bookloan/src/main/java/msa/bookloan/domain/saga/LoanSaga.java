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
    @Column(name = "status", length = 20, nullable = false)
    private SagaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 64, nullable = false)
    private LoanSagaStep currentStep;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "worker_id", length = 64)
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

    public void markProcessing(LoanSagaStep step, Duration timeout) {
        this.currentStep = step;
        this.status = SagaStatus.PROCESSING;
        LocalDateTime now = LocalDateTime.now();
        this.stepStartedAt = now;
        this.stepDeadlineAt = now.plus(timeout);
    }

    public void markCompleted() {
        this.currentStep = LoanSagaStep.FINISHED;
        this.status = SagaStatus.COMPLETED;
        this.stepDeadlineAt = null;
    }

    public void markFailed(String reason) {
        this.status = SagaStatus.FAILED;
        this.lastError = reason;
        this.stepDeadlineAt = null;
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
