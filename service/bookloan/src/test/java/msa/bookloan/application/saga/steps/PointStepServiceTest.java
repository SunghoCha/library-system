package msa.bookloan.application.saga.steps;

import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.common.events.bookloan.saga.command.ReleaseInventoryCommand;
import msa.common.events.bookloan.saga.command.ScheduleShippingCommand;
import msa.common.events.bookloan.saga.reply.point.PointChargeFailedReply;
import msa.common.events.bookloan.saga.reply.point.PointChargeFailedPayload;
import msa.common.events.bookloan.saga.reply.point.PointChargedReply;
import msa.common.events.bookloan.saga.reply.point.PointRefundedReply;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.SagaStatus;
import msa.bookloan.testsupport.time.TestClocks;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointStepServiceTest {

    @InjectMocks
    private PointStepService pointStepService;

    @Mock
    private Snowflake snowflake;

    @Mock
    private SagaTimeouts sagaTimeouts;

    @Mock
    private LoanSagaRepository sagaRepository;

    @Mock
    private CommandOutboxRecorder commandOutboxRecorder;

    @Mock
    private Clock clock;

    private LoanSaga testSaga;
    private final Clock fixedClock = TestClocks.FIXED_CLOCK;
    private final String SAGA_ID = "saga-point-123";

    @BeforeEach
    void setUp() {
        testSaga = LoanSaga.startNew(SAGA_ID, 1L, 100L, 200L, 0L, 99L, now(fixedClock));

        lenient().when(clock.instant()).thenReturn(fixedClock.instant());
        lenient().when(clock.getZone()).thenReturn(fixedClock.getZone());
    }

    @Nested
    @DisplayName("afterPointCharged 메소드 (포인트 차감 성공 시)")
    class AfterPointChargedTest {

        @Test
        @DisplayName("성공: 다음 단계(SHIPPING_SCHEDULING)로 전이하고 배송 스케줄링 커맨드를 발행한다")
        void shouldTransitionToNextStepAndRecordCommand() {
            // given
            testSaga.markProcessing(POINT_CHARGING, Duration.ofMinutes(5), now(fixedClock));
            PointChargedReply event = new PointChargedReply(1L, SAGA_ID,
                    2L, 0L, null, null, null);

            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));
            when(sagaTimeouts.stepTimeout(SHIPPING_SCHEDULING)).thenReturn(Duration.ofMinutes(10));
            when(snowflake.nextId()).thenReturn(12345L);

            // when
            pointStepService.afterPointCharged(event);

            // then
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository).saveAndFlush(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getCurrentStep()).isEqualTo(SHIPPING_SCHEDULING);

            ArgumentCaptor<ScheduleShippingCommand> commandCaptor = ArgumentCaptor.forClass(ScheduleShippingCommand.class);
            verify(commandOutboxRecorder).save(commandCaptor.capture());
            assertThat(commandCaptor.getValue().commandId()).isEqualTo(12345L);
        }

        @Test
        @DisplayName("무시: 사가가 올바른 단계(POINT_CHARGING)가 아니면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsInWrongStep() {
            // given
            testSaga.markProcessing(INVENTORY_RESERVING, Duration.ofMinutes(5), now(fixedClock)); // 이전 단계
            PointChargedReply event = new PointChargedReply(1L, SAGA_ID,
                    2L, 0L, null, null, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            pointStepService.afterPointCharged(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(commandOutboxRecorder, never()).save(any());
        }
    }

    @Nested
    @DisplayName("afterPointChargeFailed 메소드 (포인트 차감 실패 시)")
    class AfterPointChargeFailedTest {

        @Test
        @DisplayName("성공: 보상(COMPENSATING) 상태로 진입하고 재고 해제(보상) 커맨드를 발행한다")
        void shouldEnterCompensatingAndRecordCompensationCommand() {
            // given
            testSaga.markProcessing(POINT_CHARGING, Duration.ofMinutes(5), now(fixedClock));
            PointChargeFailedReply event = new PointChargeFailedReply(1L, SAGA_ID,
                    2L, 0L, "INSUFFICIENT_FUNDS", "잔액 부족");

            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));
            when(sagaTimeouts.compensationTimeoutFor()).thenReturn(Duration.ofMinutes(30));
            when(snowflake.nextId()).thenReturn(54321L);

            // when
            pointStepService.afterPointChargeFailed(event);

            // then
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository).saveAndFlush(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.COMPENSATING);

            ArgumentCaptor<ReleaseInventoryCommand> commandCaptor = ArgumentCaptor.forClass(ReleaseInventoryCommand.class);
            verify(commandOutboxRecorder).save(commandCaptor.capture());
            assertThat(commandCaptor.getValue().commandId()).isEqualTo(54321L);
        }

        @Test
        @DisplayName("무시: 사가가 이미 터미널 상태이면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsTerminal() {
            // given
            testSaga.markFailed(null); // FAILED 상태
            PointChargeFailedReply event = new PointChargeFailedReply(1L, SAGA_ID,
                    2L, 0L, null, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            pointStepService.afterPointChargeFailed(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(commandOutboxRecorder, never()).save(any());
        }
    }

    @Nested
    @DisplayName("afterPointRefunded 메소드 (포인트 환불 보상 완료 시)")
    class AfterPointRefundedTest {

        @Test
        @DisplayName("성공: 보상 단계를 이전(POINT_CHARGING)으로 이동시키고 재고 해제(보상) 커맨드를 발행한다")
        void shouldMoveCompensationStepAndRecordNextCommand() {
            // given
            // 보상 시나리오를 위한 사전 상태 설정
            testSaga.markProcessing(SHIPPING_SCHEDULING, Duration.ofMinutes(5), now(fixedClock));
            testSaga.enterCompensating(Duration.ofMinutes(30), now(fixedClock)); // 현재 상태: COMPENSATING, SHIPPING_SCHEDULING

            PointRefundedReply event = new PointRefundedReply(1L, SAGA_ID,
                    2L, 0L, null, null, null);

            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));
            when(sagaTimeouts.stepTimeout(POINT_CHARGING)).thenReturn(Duration.ofMinutes(5));
            when(snowflake.nextId()).thenReturn(98765L);

            // when
            pointStepService.afterPointRefunded(event);

            // then
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository).saveAndFlush(sagaCaptor.capture());
            LoanSaga capturedSaga = sagaCaptor.getValue();
            assertThat(capturedSaga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
            assertThat(capturedSaga.getCurrentStep()).isEqualTo(POINT_CHARGING); // 보상 단계가 역으로 이동했는지 확인

            ArgumentCaptor<ReleaseInventoryCommand> commandCaptor = ArgumentCaptor.forClass(ReleaseInventoryCommand.class);
            verify(commandOutboxRecorder).save(commandCaptor.capture());
            assertThat(commandCaptor.getValue().commandId()).isEqualTo(98765L);
        }

        @Test
        @DisplayName("무시: 사가가 올바른 보상 단계(from SHIPPING_SCHEDULING)가 아니면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsNotInCorrectCompensatingStep() {
            // given
            // 잘못된 보상 단계 설정
            testSaga.markProcessing(POINT_CHARGING, Duration.ofMinutes(5), now(fixedClock));
            testSaga.enterCompensating(Duration.ofMinutes(30), now(fixedClock)); // 현재 상태: COMPENSATING, POINT_CHARGING

            PointRefundedReply event = new PointRefundedReply(1L, SAGA_ID, 2L,
                    0L, null, null, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            pointStepService.afterPointRefunded(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(commandOutboxRecorder, never()).save(any());
        }
    }


}