package msa.bookloan.domain.saga;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import msa.common.domain.base.BaseTimeEntity;

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
    private Long triggerEventId;           // 사가 시작시킨 내부 이벤트id 추적용

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

    @Builder
    public LoanSaga(String sagaId, Long loanId, Long memberId, Long bookId,
                    Long aggregateVersion, Long triggerEventId, SagaStatus status,
                    LoanSagaStep currentStep, String lastError, String workerId,
                    LocalDateTime leaseUntil) {
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
    }

    public static LoanSaga startNew(String sagaId,
                                    Long loanId,
                                    Long memberId,
                                    Long bookId,
                                    Long aggregateVersion,
                                    Long triggerEventId) {
        return LoanSaga.builder()
                .sagaId(sagaId)
                .loanId(loanId)
                .memberId(memberId)
                .bookId(bookId)
                .aggregateVersion(aggregateVersion)
                .triggerEventId(triggerEventId)
                .status(SagaStatus.STARTED)
                .currentStep(LoanSagaStep.INIT)
                .build();
    }

    public void markProcessing(LoanSagaStep step) {
        this.currentStep = step;
        this.status = SagaStatus.PROCESSING;
    }

    public void markCompleted() {
        this.currentStep = LoanSagaStep.FINISHED;
        this.status = SagaStatus.COMPLETED;
    }

    public void markFailed(String reason) {
        this.status = SagaStatus.FAILED;
        this.lastError = reason;
    }
}
