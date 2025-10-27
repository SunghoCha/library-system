package msa.bookloan.application.saga.steps;

import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.command.RefundPointCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.shipping.ShippingAcceptedReply;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedReply;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedPayload;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledReply;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaAbortReason;
import msa.bookloan.domain.saga.SagaStatus;
import msa.bookloan.testsupport.time.TestClocks;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static java.time.LocalDateTime.now;
import static msa.bookloan.domain.saga.LoanSagaStep.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShippingStepServiceTest {

    @InjectMocks
    private ShippingStepService shippingStepService;

    @Mock
    private Snowflake snowflake;
    @Mock
    private SagaTimeouts sagaTimeouts;
    @Mock
    private LoanSagaRepository sagaRepository;
    @Mock
    private BookLoanRepository bookLoanRepository;
    @Mock
    private CommandOutboxRecorder commandOutboxRecorder;

    private LoanSaga testSaga;
    private final Clock fixedClock = TestClocks.FIXED_CLOCK;
    private final String SAGA_ID = "saga-shipping-123";
    private final Long LOAN_ID = 1L;

    @BeforeEach
    void setUp() {
        testSaga = LoanSaga.startNew(SAGA_ID, LOAN_ID, 100L, 200L, 0L, 99L, now(fixedClock));
    }

    @Nested
    @DisplayName("afterShippingAccepted 메소드 (배송 접수 완료 시)")
    class AfterShippingAcceptedTest {

        @Test
        @DisplayName("성공: 다음 단계(SHIPPING_ACCEPTED)로 상태만 전이시키고 커맨드는 발행하지 않는다")
        void shouldTransitionStateWithoutIssuingCommand() {
            // given
            testSaga.markProcessing(SHIPPING_SCHEDULING, Duration.ofMinutes(5), now(fixedClock));
            ShippingAcceptedReply event = new ShippingAcceptedReply(1L, SAGA_ID, 2L, 0L, null);

            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));
            when(sagaTimeouts.stepTimeout(SHIPPING_ACCEPTED)).thenReturn(Duration.ofMinutes(10));

            // when
            shippingStepService.afterShippingAccepted(event);

            // then
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository).saveAndFlush(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getCurrentStep()).isEqualTo(SHIPPING_ACCEPTED);

            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("실패: 사가를 찾을 수 없으면 SagaNotFoundException을 던진다")
        void shouldThrowException_whenSagaNotFound() {
            // given
            ShippingAcceptedReply event = new ShippingAcceptedReply(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> shippingStepService.afterShippingAccepted(event))
                    .isInstanceOf(SagaNotFoundException.class);
        }

        @Test
        @DisplayName("무시: 사가가 올바른 단계(SHIPPING_SCHEDULING)가 아니면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsInWrongStep() {
            // given
            testSaga.markProcessing(POINT_CHARGING, Duration.ofMinutes(5), now(fixedClock)); // 잘못된 단계
            ShippingAcceptedReply event = new ShippingAcceptedReply(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            shippingStepService.afterShippingAccepted(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("afterShippingScheduled 메소드 (배송 스케줄 완료 시)")
    class AfterShippingScheduledTest {

        @ParameterizedTest
        @EnumSource(value = LoanSagaStep.class, names = {"SHIPPING_SCHEDULING", "SHIPPING_ACCEPTED"})
        @DisplayName("성공: SHIPPING_SCHEDULING 또는 SHIPPING_ACCEPTED 단계에서 사가를 COMPLETED 상태로 완료시킨다")
        void shouldMarkSagaAsCompleted(LoanSagaStep currentStep) {
            // given
            testSaga.markProcessing(currentStep, Duration.ofMinutes(5), now(fixedClock));
            ShippingScheduledReply event = new ShippingScheduledReply(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            shippingStepService.afterShippingScheduled(event);

            // then
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository).saveAndFlush(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.COMPLETED);
            verify(bookLoanRepository).clearSagaIfMatches(LOAN_ID, SAGA_ID);
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("무시: 사가가 이미 터미널 상태이면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsTerminal() {
            // given
            testSaga.markFailed(SagaAbortReason.UNKNOWN); // FAILED 상태
            ShippingScheduledReply event = new ShippingScheduledReply(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            shippingStepService.afterShippingScheduled(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("afterShippingScheduleFailed 메소드 (배송 스케줄 실패 시)")
    class AfterShippingScheduleFailedTest {

        @ParameterizedTest
        @EnumSource(value = LoanSagaStep.class, names = {"SHIPPING_SCHEDULING", "SHIPPING_ACCEPTED"})
        @DisplayName("성공: SHIPPING_SCHEDULING 또는 SHIPPING_ACCEPTED 단계에서 보상을 시작하고 포인트 환불 커맨드를 발행한다")
        void shouldEnterCompensatingAndIssueRefundCommand(LoanSagaStep currentStep) {
            // given
            testSaga.markProcessing(currentStep, Duration.ofMinutes(5), now(fixedClock));
            ShippingScheduleFailedPayload payload = new ShippingScheduleFailedPayload("ADDRESS_INVALID", "주소 오류");
            ShippingScheduleFailedReply event = new ShippingScheduleFailedReply(1L, SAGA_ID, 2L, 0L, payload);

            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));
            when(sagaTimeouts.compensationTimeoutFor()).thenReturn(Duration.ofMinutes(30));
            when(snowflake.nextId()).thenReturn(12345L);

            // when
            shippingStepService.afterShippingScheduleFailed(event);

            // then
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository).saveAndFlush(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.COMPENSATING);

            ArgumentCaptor<RefundPointCommand> commandCaptor = ArgumentCaptor.forClass(RefundPointCommand.class);
            verify(commandOutboxRecorder).save(commandCaptor.capture());
            assertThat(commandCaptor.getValue().commandId()).isEqualTo(12345L);
        }

        @Test
        @DisplayName("무시: 사가가 올바른 단계가 아니면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsInWrongStep() {
            // given
            testSaga.markProcessing(POINT_CHARGING, Duration.ofMinutes(5), now(fixedClock)); // 잘못된 단계
            ShippingScheduleFailedReply event = new ShippingScheduleFailedReply(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            shippingStepService.afterShippingScheduleFailed(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(commandOutboxRecorder, never()).save(any());
        }
    }
}