package msa.bookloan.application.saga.steps;

import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.command.ChargePointCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.inventory.InventoryReleasedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedInternalEvent;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedPayload;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedInternalEvent;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.SagaAbortReason;
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
import static msa.bookloan.domain.saga.LoanSagaStep.INVENTORY_RESERVING;
import static msa.bookloan.domain.saga.LoanSagaStep.POINT_CHARGING;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryStepServiceTest {

    @InjectMocks
    private InventoryStepService inventoryStepService;

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
    private final String SAGA_ID = "saga-123";
    private final Long LOAN_ID = 1L;
    private final Long BOOK_ID = 200L;

    @BeforeEach
    void setUp() {
        // 각 테스트에서 재사용할 기본 LoanSaga 객체 생성 (PROCESSING)
        testSaga = LoanSaga.startNew(SAGA_ID, LOAN_ID, 100L, 200L, 0L, 99L, now(fixedClock));
    }

    @Nested
    @DisplayName("afterInventoryReserved 메소드 (재고 예약 성공 시)")
    class AfterInventoryReservedTest {

        @Test
        @DisplayName("성공: 유효한 상태의 사가를 다음 단계(POINT_CHARGING)로 전이시키고 포인트 차감 커맨드를 발행한다")
        void shouldTransitionToNextStepAndRecordCommand_whenSagaIsValid() {
            // given
            // 사가를 INVENTORY_RESERVING 단계로 설정
            testSaga.markProcessing(INVENTORY_RESERVING, Duration.ofMinutes(5), now(fixedClock));
            InventoryReservedInternalEvent event = new InventoryReservedInternalEvent(1L, SAGA_ID, 2L, 0L, null);

            // Mock 설정
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));
            when(sagaTimeouts.stepTimeout(POINT_CHARGING)).thenReturn(Duration.ofMinutes(5));
            when(snowflake.nextId()).thenReturn(12345L);

            // when
            inventoryStepService.afterInventoryReserved(event);

            // then
            // Saga가 저장되었는지 검증
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository, times(1)).saveAndFlush(sagaCaptor.capture());
            LoanSaga capturedSaga = sagaCaptor.getValue();
            assertThat(capturedSaga.getStatus()).isEqualTo(SagaStatus.PROCESSING);
            assertThat(capturedSaga.getCurrentStep()).isEqualTo(POINT_CHARGING);

            // Outbox에 Command가 저장되었는지 검증
            ArgumentCaptor<ChargePointCommand> commandCaptor = ArgumentCaptor.forClass(ChargePointCommand.class);
            verify(commandOutboxRecorder, times(1)).save(commandCaptor.capture());
            ChargePointCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.sagaId()).isEqualTo(SAGA_ID);
            assertThat(capturedCommand.commandId()).isEqualTo(12345L);
        }

        @Test
        @DisplayName("실패: 사가를 찾을 수 없으면 SagaNotFoundException을 던진다")
        void shouldThrowException_whenSagaNotFound() {
            // given
            InventoryReservedInternalEvent event = new InventoryReservedInternalEvent(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> inventoryStepService.afterInventoryReserved(event))
                    .isInstanceOf(SagaNotFoundException.class);

            // 다른 의존성은 호출되지 않았는지 검증
            verify(commandOutboxRecorder, never()).save(any());
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString());
        }

        @Test
        @DisplayName("무시: 사가가 이미 터미널 상태(예: FAILED)이면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsInTerminalState() {
            // given
            testSaga.markFailed(SagaAbortReason.UNKNOWN); // FAILED 상태로 설정
            InventoryReservedInternalEvent event = new InventoryReservedInternalEvent(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            inventoryStepService.afterInventoryReserved(event);

            // then
            // 상태 변경이나 저장이 일어나지 않아야 함
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("무시: 사가가 올바른 단계(INVENTORY_RESERVING)가 아니면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsInWrongStep() {
            // given
            // INIT 단계에 머물러 있는 상태
            InventoryReservedInternalEvent event = new InventoryReservedInternalEvent(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            inventoryStepService.afterInventoryReserved(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(commandOutboxRecorder, never()).save(any());
        }
    }

    @Nested
    @DisplayName("afterInventoryReserveFailed 메소드 (재고 예약 실패 시)")
    class AfterInventoryReserveFailedTest {

        @Test
        @DisplayName("성공: 유효한 상태의 사가를 FAILED로 전이시키고 BookLoan의 sagaId를 정리한다(시맨틱 락 해제)")
        void shouldTransitionToFailedAndClearBinding_whenSagaIsValid() {
            // given
            testSaga.markProcessing(INVENTORY_RESERVING, Duration.ofMinutes(5), now(fixedClock));
            InventoryReserveFailedPayload payload = new InventoryReserveFailedPayload(BOOK_ID, "NO_STOCK", "재고 부족");
            InventoryReserveFailedInternalEvent event = new InventoryReserveFailedInternalEvent(1L, SAGA_ID, 2L, 0L, payload);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            inventoryStepService.afterInventoryReserveFailed(event);

            // then
            // 1. Saga가 FAILED 상태로 저장되었는지 검증
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository, times(1)).saveAndFlush(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(sagaCaptor.getValue().getLastError()).isEqualTo(SagaAbortReason.INVENTORY_RESERVE_FAILED.name());

            // 2. BookLoan의 sagaId가 정리되었는지 검증
            verify(bookLoanRepository, times(1)).clearSagaIfMatches(LOAN_ID, SAGA_ID);
        }

        @Test
        @DisplayName("무시: 사가가 올바른 단계(INVENTORY_RESERVING)가 아니면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsInWrongStep() {
            // given
            // POINT_CHARGING 단계로 미리 넘어간 상태 (경합에서 이겨서 이미 다음 성공흐름으로 진행됨)
            testSaga.markProcessing(POINT_CHARGING, Duration.ofMinutes(5), now(fixedClock));
            InventoryReserveFailedPayload payload = new InventoryReserveFailedPayload(BOOK_ID, "NO_STOCK", "재고 부족");
            InventoryReserveFailedInternalEvent event = new InventoryReserveFailedInternalEvent(1L, SAGA_ID, 2L, 0L, payload);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            inventoryStepService.afterInventoryReserveFailed(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("afterInventoryReleased 메소드 (재고 해제 보상 완료 시)")
    class AfterInventoryReleasedTest {

        @Test
        @DisplayName("성공: 보상 중인 사가를 FAILED(COMPENSATION)로 확정하고 BookLoan의 sagaId를 정리한다")
        void shouldFinalizeAsFailed_whenCompensating() {
            // given
            // 보상 중인 상태로 설정
            testSaga.markProcessing(POINT_CHARGING, Duration.ofMinutes(5), now(fixedClock));
            testSaga.enterCompensating(Duration.ofMinutes(5), now(fixedClock));
            InventoryReleasedInternalEvent event = new InventoryReleasedInternalEvent(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            inventoryStepService.afterInventoryReleased(event);

            // then
            // 1. Saga가 FAILED 상태로 저장되었는지 검증
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository, times(1)).saveAndFlush(sagaCaptor.capture());
            LoanSaga capturedSaga = sagaCaptor.getValue();
            assertThat(capturedSaga.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(capturedSaga.getLastError()).isEqualTo(SagaAbortReason.COMPENSATION.name());

            // 2. BookLoan의 sagaId가 정리되었는지 검증
            verify(bookLoanRepository, times(1)).clearSagaIfMatches(LOAN_ID, SAGA_ID);
        }

        @Test
        @DisplayName("무시: 사가가 보상 중(COMPENSATING) 상태가 아니면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsNotCompensating() {
            // given
            // 정상 진행(PROCESSING) 상태
            testSaga.markProcessing(POINT_CHARGING, Duration.ofMinutes(5), now(fixedClock));
            InventoryReleasedInternalEvent event = new InventoryReleasedInternalEvent(1L, SAGA_ID, 2L, 0L, null);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            inventoryStepService.afterInventoryReleased(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString());
        }
    }

}