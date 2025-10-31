package msa.bookloan.domain.saga;

import msa.bookloan.testsupport.time.TestClocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import static java.time.LocalDateTime.*;
import static org.assertj.core.api.Assertions.assertThat;

class LoanSagaTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private final Clock fixedClock = TestClocks.FIXED_CLOCK;

    private LoanSaga createInitialSaga() {
        return LoanSaga.startNew( // PROCESSING 으로 생성
                123456L,
                1L,
                100L,
                200L,
                0L,
                99L,
                now(fixedClock)
        );
    }

    @Nested
    @DisplayName("Saga 시작")
    class SagaStartTest {

        @Test
        @DisplayName("성공: startNew() 팩토리 메소드로 생성 시 초기 상태가 올바르게 설정된다")
        void startNew_ShouldInitializeCorrectly() {
            // given & when
            LoanSaga saga = createInitialSaga();

            // then
            assertThat(saga.getId()).isEqualTo(123456L);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.PROCESSING);
            assertThat(saga.getCurrentStep()).isEqualTo(LoanSagaStep.INIT);
            assertThat(saga.getStepStartedAt()).isNotNull();
            assertThat(saga.getStepDeadlineAt()).isNull(); // 초기에는 데드라인 없음
        }
    }

    @Nested
    @DisplayName("정상 진행 상태 전이 (markProcessing)")
    class MarkProcessingTest {

        @Test
        @DisplayName("성공: 다음 스텝으로 상태가 정상 전이된다")
        void markProcessing_ShouldTransitionToNextStep() {
            // given
            LoanSaga saga = createInitialSaga();

            // when
            boolean result = saga.markProcessing(LoanSagaStep.MEMBER_CHECKING, DEFAULT_TIMEOUT, now(fixedClock));

            // then
            assertThat(result).isTrue();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.PROCESSING);
            assertThat(saga.getCurrentStep()).isEqualTo(LoanSagaStep.MEMBER_CHECKING);
            assertThat(saga.getStepDeadlineAt()).isAfter(now(fixedClock));
        }

        @Test
        @DisplayName("실패: 이미 터미널(완료/실패 등) 상태일 경우 상태 전이가 일어나지 않는다")
        void markProcessing_ShouldNotTransition_WhenInTerminalState() {
            // given
            LoanSaga saga = createInitialSaga();
            // SHIPPING_SCHEDULING 단계로 이동 후 강제로 완료 상태로 만듦
            saga.markProcessing(LoanSagaStep.SHIPPING_SCHEDULING, DEFAULT_TIMEOUT, now(fixedClock));
            saga.markCompleted();

            // when
            boolean result = saga.markProcessing(LoanSagaStep.MEMBER_CHECKING, DEFAULT_TIMEOUT, now(fixedClock));

            // then
            assertThat(result).isFalse();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED); // 상태 유지
            assertThat(saga.getCurrentStep()).isEqualTo(LoanSagaStep.FINISHED);
        }

        @Test
        @DisplayName("멱등성: 동일한 스텝으로 재호출 시 상태가 변경되지 않고 false를 반환한다")
        void markProcessing_ShouldBeIdempotent_ForSameStep() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.MEMBER_CHECKING, DEFAULT_TIMEOUT, now(fixedClock));
            LocalDateTime initialDeadline = saga.getStepDeadlineAt();

            // when
            boolean result = saga.markProcessing(LoanSagaStep.MEMBER_CHECKING, DEFAULT_TIMEOUT, now(fixedClock));

            // then
            assertThat(result).isFalse();
            assertThat(saga.getStepDeadlineAt()).isEqualTo(initialDeadline); // 데드라인도 변경되지 않음
        }
    }

    @Nested
    @DisplayName("완료 상태 전이 (markCompleted)")
    class MarkCompletedTest {

        @Test
        @DisplayName("성공: 배송 스케줄링(SHIPPING_SCHEDULING) 단계에서 완료 처리된다")
        void markCompleted_ShouldSucceed_FromShippingSchedulingStep() {
            // given
            LoanSaga saga = createInitialSaga();
            // SHIPPING_SCHEDULING 단계까지 강제로 진행
            saga.markProcessing(LoanSagaStep.SHIPPING_SCHEDULING, DEFAULT_TIMEOUT, now(fixedClock));

            // when
            boolean result = saga.markCompleted();

            // then
            assertThat(result).isTrue();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(saga.getCurrentStep()).isEqualTo(LoanSagaStep.FINISHED);
            assertThat(saga.getStepDeadlineAt()).isNull();
            assertThat(saga.getLastError()).isNull();
        }

        @Test
        @DisplayName("실패: 허용되지 않은 스텝(MEMBER_CHECKING 등..)에서는 완료 처리되지 않는다")
        void markCompleted_ShouldFail_FromNonAllowedStep() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.MEMBER_CHECKING, DEFAULT_TIMEOUT, now(fixedClock));

            // when
            boolean result = saga.markCompleted();

            // then
            assertThat(result).isFalse();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.PROCESSING);
            assertThat(saga.getCurrentStep()).isEqualTo(LoanSagaStep.MEMBER_CHECKING);
        }

        @Test
        @DisplayName("멱등성: 이미 완료된 상태에서 재호출 시 상태가 변경되지 않는다")
        void markCompleted_ShouldBeIdempotent() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.SHIPPING_SCHEDULING, DEFAULT_TIMEOUT, now(fixedClock));
            saga.markCompleted();

            // when
            boolean result = saga.markCompleted();

            // then
            assertThat(result).isFalse();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("실패 및 취소 상태 전이 (markFailed, markCancelled)")
    class MarkFailedAndCancelledTest {
        @Test
        @DisplayName("성공: markFailed 호출 시 FAILED 상태로 전이된다")
        void markFailed_ShouldTransitionToFailed() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.INVENTORY_RESERVING, DEFAULT_TIMEOUT, now(fixedClock));

            // when
            boolean result = saga.markFailed(SagaAbortReason.INVENTORY_RESERVE_FAILED);

            // then
            assertThat(result).isTrue();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(saga.getLastError()).isEqualTo(SagaAbortReason.INVENTORY_RESERVE_FAILED.name());
            assertThat(saga.getStepDeadlineAt()).isNull();
        }

        @Test
        @DisplayName("실패: 이미 COMPLETED 상태인 경우 FAILED로 변경할 수 없다")
        void markFailed_ShouldNotTransition_WhenAlreadyCompleted() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.SHIPPING_SCHEDULING, DEFAULT_TIMEOUT, now(fixedClock));
            saga.markCompleted();

            // when
            boolean result = saga.markFailed(SagaAbortReason.UNKNOWN);

            // then
            assertThat(result).isFalse();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }

        @Test
        @DisplayName("성공: markCancelled 호출 시 CANCELLED 상태로 전이된다")
        void markCancelled_ShouldTransitionToCancelled() {
            // given
            LoanSaga saga = createInitialSaga();

            // when
            boolean result = saga.markCancelled(SagaAbortReason.USER_CANCEL);

            // then
            assertThat(result).isTrue();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.CANCELLED);
            assertThat(saga.getLastError()).isEqualTo(SagaAbortReason.USER_CANCEL.name());
        }
    }

    @Nested
    @DisplayName("보상 상태 전이 (enterCompensating, moveCompensatingTo)")
    class CompensationFlowTest {
        @Test
        @DisplayName("성공: enterCompensating 호출 시 COMPENSATING 상태로 진입한다")
        void enterCompensating_ShouldStartCompensation() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.POINT_CHARGING, DEFAULT_TIMEOUT, now(fixedClock)); // 보상 가능한 스텝

            // when
            boolean result = saga.enterCompensating(DEFAULT_TIMEOUT, now(fixedClock));

            // then
            assertThat(result).isTrue();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(saga.getCurrentStep()).isEqualTo(LoanSagaStep.POINT_CHARGING); // 현재 스텝은 유지
            assertThat(saga.getStepDeadlineAt()).isNotNull();
        }

        @Test
        @DisplayName("실패: 피벗(Pivot) 트랜잭션 이후에는 보상 상태로 진입할 수 없다")
        void enterCompensating_ShouldFail_AfterPivot() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.SHIPPING_SCHEDULING, DEFAULT_TIMEOUT, now(fixedClock));
            saga.markCompleted(); // isAfterPivot()가 true가 됨

            // when
            boolean result = saga.enterCompensating(DEFAULT_TIMEOUT, now(fixedClock));

            // then
            assertThat(result).isFalse();
            assertThat(saga.getStatus()).isNotEqualTo(SagaStatus.COMPENSATING);
        }

        @Test
        @DisplayName("성공: moveCompensatingTo 호출 시 보상 스텝이 변경된다")
        void moveCompensatingTo_ShouldMoveToNextCompensationStep() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.SHIPPING_SCHEDULING, DEFAULT_TIMEOUT, now(fixedClock));
            saga.enterCompensating(DEFAULT_TIMEOUT, now(fixedClock));

            // when
            boolean result = saga.moveCompensatingTo(LoanSagaStep.POINT_CHARGING, DEFAULT_TIMEOUT, now(fixedClock));

            // then
            assertThat(result).isTrue();
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(saga.getCurrentStep()).isEqualTo(LoanSagaStep.POINT_CHARGING);
            assertThat(saga.getStepDeadlineAt()).isAfter(now(fixedClock));
        }

        @Test
        @DisplayName("실패: 보상 중인 상태가 아니면 moveCompensatingTo가 동작하지 않는다")
        void moveCompensatingTo_ShouldFail_IfNotCompensating() {
            // given
            LoanSaga saga = createInitialSaga();
            saga.markProcessing(LoanSagaStep.SHIPPING_SCHEDULING, DEFAULT_TIMEOUT, now(fixedClock));

            // when
            boolean result = saga.moveCompensatingTo(LoanSagaStep.POINT_CHARGING, DEFAULT_TIMEOUT, now(fixedClock));

            // then
            assertThat(result).isFalse();
            assertThat(saga.getCurrentStep()).isEqualTo(LoanSagaStep.SHIPPING_SCHEDULING);
        }
    }


}