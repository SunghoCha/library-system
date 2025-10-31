package msa.bookloan.application.saga;

import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
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
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaAbortReason;
import msa.bookloan.domain.saga.SagaStatus;
import msa.common.events.bookloan.saga.command.*;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static msa.bookloan.domain.saga.LoanSagaStep.MEMBER_CHECKING;
import static msa.bookloan.domain.saga.LoanSagaStep.SHIPPING_SCHEDULING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanRequestSagaOrchestratorTest {

    @InjectMocks
    private LoanRequestSagaOrchestrator orchestrator;

    @Mock
    private Clock clock;

    @Mock
    private Snowflake snowflake;

    @Mock
    private LoanSagaRepository sagaRepository;

    @Mock
    private CommandOutboxRecorder commandOutboxRecorder;

    @Mock
    private SagaTimeouts sagaTimeouts;

    @Mock
    private BookLoanRepository bookLoanRepository;

    @Mock
    private MemberStepService memberStepService;

    @Mock
    private InventoryStepService inventoryStepService;

    @Mock
    private PointStepService pointStepService;

    @Mock
    private ShippingStepService shippingStepService;

    @Captor
    private ArgumentCaptor<CheckMemberCommand> commandCaptor;

    @Captor
    private ArgumentCaptor<ReleaseInventoryCommand> releaseInventoryCommandCaptor;

    @Captor
    private ArgumentCaptor<RefundPointCommand> refundPointCommandCaptor;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2000, 10, 20, 10, 0, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final Duration STEP_TIMEOUT = Duration.ofMinutes(5);
    private static final LocalDateTime EXPECTED_DEADLINE = FIXED_NOW.plus(STEP_TIMEOUT);
    private static final long MOCK_COMMAND_ID = 999L;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED_CLOCK.instant());
        lenient().when(clock.getZone()).thenReturn(FIXED_CLOCK.getZone());
        lenient().when(snowflake.nextId()).thenReturn(MOCK_COMMAND_ID);
        lenient().when(sagaTimeouts.stepTimeout(MEMBER_CHECKING)).thenReturn(STEP_TIMEOUT);
    }

    @Nested
    @DisplayName("start: 사가 시작")
    class StartTests {

        private LoanRequestedInternalEvent createEvent() {
            return new LoanRequestedInternalEvent(
                    123L,  // sagaId
                    100L,  // loanId
                    200L,  // memberId
                    300L,  // bookId
                    1L,    // aggregateVersion
                    900L,
                    FIXED_NOW// eventId (triggerEventId)
            );
        }

        @Test
        @DisplayName("start 성공: 사가 바인딩 및 생성이 성공하고 멤버 확인 커맨드를 발행한다.")
        void start_Success() {
            // given
            LoanRequestedInternalEvent event = createEvent();
            long sagaId = event.sagaId();
            long loanId = event.loanId();
            long memberId = event.memberId();
            long bookId = event.bookId();
            long aggVer = event.aggregateVersion();
            long eventId = event.eventId();

            when(bookLoanRepository.tryBindSaga(anyLong(), anyLong())).thenReturn(1);

            when(sagaRepository.insertIfAbsent(
                    anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
                    anyString(), anyString(), any(LocalDateTime.class)
            )).thenReturn(true);

            when(commandOutboxRecorder.save(any(CheckMemberCommand.class))).thenReturn(true);

            // When
            orchestrator.start(event);

            // Then
            verify(bookLoanRepository).tryBindSaga(eq(loanId), eq(sagaId));

            verify(sagaRepository).insertIfAbsent(
                    eq(sagaId),
                    eq(loanId),
                    eq(memberId),
                    eq(bookId),
                    eq(aggVer),
                    eq(eventId),
                    eq(SagaStatus.PROCESSING.name()),
                    eq(MEMBER_CHECKING.name()),
                    eq(EXPECTED_DEADLINE)
            );

            verify(commandOutboxRecorder).save(commandCaptor.capture());
            CheckMemberCommand capturedCommand = commandCaptor.getValue();

            assertThat(capturedCommand.commandId()).isEqualTo(MOCK_COMMAND_ID);
            assertThat(capturedCommand.sagaId()).isEqualTo(event.sagaId());
            assertThat(capturedCommand.loanId()).isEqualTo(event.loanId());
            assertThat(capturedCommand.memberId()).isEqualTo(event.memberId());
            assertThat(capturedCommand.causationEventId()).isEqualTo(event.eventId());
            assertThat(capturedCommand.type()).isEqualTo(SagaCommandType.MEMBER_CHECK.getValue());
        }

        @Test
        @DisplayName("start 실패: 바인딩 실패(tryBindSaga=0) 시 즉시 반환한다.")
        void start_FailsOnBind() {
            // given
            LoanRequestedInternalEvent event = createEvent();

            when(bookLoanRepository.tryBindSaga(anyLong(), anyLong())).thenReturn(0);

            // when
            orchestrator.start(event);

            // then
            verify(bookLoanRepository).tryBindSaga(anyLong(), anyLong());

            verify(sagaRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("start 실패: 사가 Row 중복(insertIfAbsent=false) 시 즉시 반환한다.")
        void start_FailsOnSagaCreate() {
            // given
            LoanRequestedInternalEvent event = createEvent();
            long sagaId = event.sagaId();
            long loanId = event.loanId();
            long memberId = event.memberId();
            long bookId = event.bookId();
            long aggVer = event.aggregateVersion();
            long eventId = event.eventId();

            when(bookLoanRepository.tryBindSaga(anyLong(), anyLong())).thenReturn(1);

            when(sagaRepository.insertIfAbsent(
                    anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
                    anyString(), anyString(), any(LocalDateTime.class)
            )).thenReturn(false);

            // when
            orchestrator.start(event);

            // then
            verify(bookLoanRepository).tryBindSaga(eq(loanId), eq(sagaId));

            verify(sagaRepository).insertIfAbsent(
                    eq(sagaId),
                    eq(loanId),
                    eq(memberId),
                    eq(bookId),
                    eq(aggVer),
                    eq(eventId),
                    eq(SagaStatus.PROCESSING.name()),
                    eq(MEMBER_CHECKING.name()),
                    eq(EXPECTED_DEADLINE)
            );

            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("실패(멱등성): 동일 이벤트 2회 수신 시, 2번째는 tryBindSaga에서 차단된다.")
        void start_Idempotency_FailsOnBind() {
            // given
            LoanRequestedInternalEvent event = createEvent();
            long sagaId = event.sagaId();
            long loanId = event.loanId();

            // 첫 번째 호출에는 1(성공) 반환, 두 번째 호출에는 0(실패) 반환
            when(bookLoanRepository.tryBindSaga(anyLong(), anyLong()))
                    .thenReturn(1, 0);

            // 첫 번째 호출이 통과할 경우를 대비한 스터빙
            when(sagaRepository.insertIfAbsent(
                    anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
                    anyString(), anyString(), any(LocalDateTime.class)
            )).thenReturn(true);
            when(commandOutboxRecorder.save(any(CheckMemberCommand.class))).thenReturn(true);

            // when
            orchestrator.start(event); // 첫 번째 호출 (성공)
            orchestrator.start(event); // 두 번째 호출 (실패)

            // then
            // tryBindSaga는 2번 모두 시도되어야 함
            verify(bookLoanRepository, times(2)).tryBindSaga(eq(loanId), eq(sagaId));

            // insertIfAbsent와 save는 첫 번째 성공한 호출에서 "단 1회"만 실행되어야 함
            verify(sagaRepository, times(1)).insertIfAbsent(
                    // (첫 번째 호출에 대한 상세 검증. 이미 다른 테스트에서 다루므로 any()로 대체해도 무방)
                    eq(sagaId), eq(loanId), anyLong(), anyLong(), anyLong(), anyLong(),
                    anyString(), anyString(), any(LocalDateTime.class)
            );
            verify(commandOutboxRecorder, times(1)).save(any(CheckMemberCommand.class));
        }

        @Test
        @DisplayName("실패(멱등성/경합): 2회 수신이 tryBindSaga를 통과해도, insertIfAbsent에서 차단된다.")
        void start_Idempotency_FailsOnInsert() {
            // given
            // (이 시나리오는 tryBindSaga가 멱등성 보장을 못 하거나,
            //  두 스레드가 동시에 tryBindSaga(0)을 통과한 경합 상태를 가정)
            LoanRequestedInternalEvent event = createEvent();
            long sagaId = event.sagaId();
            long loanId = event.loanId();

            // 1. tryBindSaga는 두 번 다 통과 (1 반환)
            when(bookLoanRepository.tryBindSaga(anyLong(), anyLong()))
                    .thenReturn(1, 1);

            // 2. insertIfAbsent는 첫 번째는 성공(true), 두 번째는 실패(false)
            when(sagaRepository.insertIfAbsent(
                    anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
                    anyString(), anyString(), any(LocalDateTime.class)
            )).thenReturn(true, false);

            when(commandOutboxRecorder.save(any(CheckMemberCommand.class))).thenReturn(true);

            // when
            orchestrator.start(event); // 1. 첫 번째 호출 (성공)
            orchestrator.start(event); // 2. 두 번째 호출 (실패)

            // then
            // 1. tryBindSaga는 2번 모두 시도됨
            verify(bookLoanRepository, times(2)).tryBindSaga(eq(loanId), eq(sagaId));

            // 2. insertIfAbsent도 2번 모두 시도됨
            verify(sagaRepository, times(2)).insertIfAbsent(
                    eq(sagaId), eq(loanId), anyLong(), anyLong(), anyLong(), anyLong(),
                    anyString(), anyString(), any(LocalDateTime.class)
            );

            // 3. 하지만 save(부수 효과)는 첫 번째 성공한 호출에서 "단 1회"만 실행되어야 함
            verify(commandOutboxRecorder, times(1)).save(any(CheckMemberCommand.class));
        }

    }

    @Nested
    @DisplayName("onMemberChecked: 멤버 확인 응답 처리")
    class OnMemberCheckedTests {

        private MemberCheckedInternalEvent createEvent() {
            return new MemberCheckedInternalEvent(
                    1L,   // eventId
                    123L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    200L, // memberId
                    false,// blacklisted
                    null  // reason
            );
        }

        @Test
        @DisplayName("성공: MemberStepService로 처리를 위임한다.")
        void onMemberChecked_Success() {
            // given
            MemberCheckedInternalEvent event = createEvent();

            doNothing().when(memberStepService).afterMemberChecked(any(MemberCheckedInternalEvent.class));

            // when
            orchestrator.onMemberChecked(event);

            // then
            verify(memberStepService, times(1)).afterMemberChecked(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onMemberChecked_OptimisticLockFailure() {
            // given
            MemberCheckedInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(memberStepService).afterMemberChecked(any(MemberCheckedInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onMemberChecked(event));

            verify(memberStepService, times(1)).afterMemberChecked(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onMemberChecked_GenericException() {
            // given
            MemberCheckedInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(memberStepService).afterMemberChecked(any(MemberCheckedInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onMemberChecked(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);

            verify(memberStepService, times(1)).afterMemberChecked(eq(event));
        }
    }

    @Nested
    @DisplayName("onInventoryReserved: 재고 예약 응답 처리")
    class OnInventoryReservedTests {

        private InventoryReservedInternalEvent createEvent() {
            return new InventoryReservedInternalEvent(
                    1L,   // eventId
                    456L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    300L, // bookId
                    88L   // reservationId
            );
        }

        @Test
        @DisplayName("성공: InventoryStepService로 처리를 위임한다.")
        void onInventoryReserved_Success() {
            // given
            InventoryReservedInternalEvent event = createEvent();

            doNothing().when(inventoryStepService).afterInventoryReserved(any(InventoryReservedInternalEvent.class));

            // when
            orchestrator.onInventoryReserved(event);

            // then
            // event 객체가 정확히 전달되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserved(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onInventoryReserved_OptimisticLockFailure() {
            // given
            InventoryReservedInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(inventoryStepService).afterInventoryReserved(any(InventoryReservedInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReserved(event));

            // 예외가 발생했더라도 inventoryStepService.afterInventoryReserved가 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserved(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onInventoryReserved_GenericException() {
            // given
            InventoryReservedInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(inventoryStepService).afterInventoryReserved(any(InventoryReservedInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onInventoryReserved(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);


            // 예외가 발생했더라도 inventoryStepService.afterInventoryReserved가 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserved(eq(event));
        }
    }

    @Nested
    @DisplayName("onInventoryReserveFailed: 재고 예약 실패 응답 처리")
    class OnInventoryReserveFailedTests {

        private InventoryReserveFailedInternalEvent createEvent() {
            // 레코드는 단순 데이터 객체이므로 new로 생성
            return new InventoryReserveFailedInternalEvent(
                    1L,   // eventId
                    789L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    300L, // bookId
                    "NO_STOCK", // reasonCode
                    "재고 없음"  // message
            );
        }

        @Test
        @DisplayName("성공: InventoryStepService로 처리를 위임한다.")
        void onInventoryReserveFailed_Success() {
            // given
            InventoryReserveFailedInternalEvent event = createEvent();

            doNothing().when(inventoryStepService).afterInventoryReserveFailed(any(InventoryReserveFailedInternalEvent.class));

            // when
            orchestrator.onInventoryReserveFailed(event);

            // then
            // event 객체가 정확히 전달되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserveFailed(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onInventoryReserveFailed_OptimisticLockFailure() {
            // given
            InventoryReserveFailedInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(inventoryStepService).afterInventoryReserveFailed(any(InventoryReserveFailedInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReserveFailed(event));

            // 예외가 발생했더라도 inventoryStepService.afterInventoryReserveFailed가 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserveFailed(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onInventoryReserveFailed_GenericException() {
            // given
            InventoryReserveFailedInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 에외";

            doThrow(new RuntimeException(errorMessage))
                    .when(inventoryStepService).afterInventoryReserveFailed(any(InventoryReserveFailedInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onInventoryReserveFailed(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);

            // 예외가 발생했더라도 inventoryStepService.afterInventoryReserveFailed가 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserveFailed(eq(event));
        }

        @Nested
        @DisplayName("onPointCharged: 포인트 차감 응답 처리")
        class OnPointChargedTests {

            private PointChargedInternalEvent createEvent() {
                return new PointChargedInternalEvent(
                        1L,   // eventId
                        111L, // sagaId
                        2L,   // causationCommandId
                        0L,   // loanVersion
                        200L, // memberId
                        100L, // amount
                        999L  // paymentId
                );
            }

            @Test
            @DisplayName("성공: PointStepService로 처리를 위임한다.")
            void onPointCharged_Success() {
                // given
                PointChargedInternalEvent event = createEvent();

                doNothing().when(pointStepService).afterPointCharged(any(PointChargedInternalEvent.class));

                // when
                orchestrator.onPointCharged(event);

                // then
                // event 객체가 정확히 전달되었는지 검증
                verify(pointStepService, times(1)).afterPointCharged(eq(event));
            }

            @Test
            @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
            void onPointCharged_OptimisticLockFailure() {
                // given
                PointChargedInternalEvent event = createEvent();
                String errorMessage = "사가 버전 불일치";

                doThrow(new OptimisticLockingFailureException(errorMessage))
                        .when(pointStepService).afterPointCharged(any(PointChargedInternalEvent.class));

                // when then
                assertDoesNotThrow(() -> orchestrator.onPointCharged(event));

                // 예외가 발생했더라도 pointStepService.afterPointCharged가 호출 시도되었는지 검증
                verify(pointStepService, times(1)).afterPointCharged(eq(event));
            }

            @Test
            @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
            void onPointCharged_GenericException() {
                // given
                PointChargedInternalEvent event = createEvent();
                String errorMessage = "예상치 못한 예외";

                doThrow(new RuntimeException(errorMessage))
                        .when(pointStepService).afterPointCharged(any(PointChargedInternalEvent.class));

                // when then
                assertThatThrownBy(() -> orchestrator.onPointCharged(event))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining(errorMessage);

                // 예외가 발생했더라도 pointStepService.afterPointCharged가 호출 시도되었는지 검증
                verify(pointStepService, times(1)).afterPointCharged(eq(event));
            }
        }
    }

    @Nested
    @DisplayName("onPointChargeFailed: 포인트 차감 실패 응답 처리")
    class OnPointChargeFailedTests {

        private PointChargeFailedInternalEvent createEvent() {
            // 레코드는 단순 데이터 객체이므로 new로 생성
            return new PointChargeFailedInternalEvent(
                    1L,   // eventId
                    222L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    "NO_POINT", // reasonCode
                    "잔액 부족"  // message
            );
        }

        @Test
        @DisplayName("성공: PointStepService로 처리를 위임한다.")
        void onPointChargeFailed_Success() {
            // given
            PointChargeFailedInternalEvent event = createEvent();

            doNothing().when(pointStepService).afterPointChargeFailed(any(PointChargeFailedInternalEvent.class));

            // when
            orchestrator.onPointChargeFailed(event);

            // then
            // event 객체가 정확히 전달되었는지 검증
            verify(pointStepService, times(1)).afterPointChargeFailed(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onPointChargeFailed_OptimisticLockFailure() {
            // given
            PointChargeFailedInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(pointStepService).afterPointChargeFailed(any(PointChargeFailedInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onPointChargeFailed(event));

            // 예외가 발생했더라도 pointStepService.afterPointChargeFailed가 호출 시도되었는지 검증
            verify(pointStepService, times(1)).afterPointChargeFailed(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onPointChargeFailed_GenericException() {
            // given
            PointChargeFailedInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(pointStepService).afterPointChargeFailed(any(PointChargeFailedInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onPointChargeFailed(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);

            // 예외가 발생했더라도 pointStepService.afterPointChargeFailed가 호출 시도되었는지 검증
            verify(pointStepService, times(1)).afterPointChargeFailed(eq(event));
        }
    }

    @Nested
    @DisplayName("onShippingAccepted: 배송 수락 응답 처리")
    class OnShippingAcceptedTests {

        private ShippingAcceptedInternalEvent createEvent() {
            // 레코드는 단순 데이터 객체이므로 new로 생성
            return new ShippingAcceptedInternalEvent(
                    1L,   // eventId
                    333L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    300L, // bookId
                    111L, // provisionalShipmentId (nullable)
                    "p-123" // trackingNoPreview (nullable)
            );
        }

        @Test
        @DisplayName("성공: ShippingStepService로 처리를 위임한다.")
        void onShippingAccepted_Success() {
            // given
            ShippingAcceptedInternalEvent event = createEvent();

            doNothing().when(shippingStepService).afterShippingAccepted(any(ShippingAcceptedInternalEvent.class));

            // when
            orchestrator.onShippingAccepted(event);

            // then
            // event 객체 정확히 전달되었는지 검증
            verify(shippingStepService, times(1)).afterShippingAccepted(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onShippingAccepted_OptimisticLockFailure() {
            // given
            ShippingAcceptedInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(shippingStepService).afterShippingAccepted(any(ShippingAcceptedInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingAccepted(event));

            // 예외가 발생했더라도 shippingStepService.afterShippingAccepted 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingAccepted(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onShippingAccepted_GenericException() {
            // given
            ShippingAcceptedInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(shippingStepService).afterShippingAccepted(any(ShippingAcceptedInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onShippingAccepted(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);

            // 예외가 발생했더라도 shippingStepService.afterShippingAccepted 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingAccepted(eq(event));
        }
    }

    @Nested
    @DisplayName("onShippingScheduled: 배송 스케줄 성공 응답 처리")
    class OnShippingScheduledTests {

        private ShippingScheduledInternalEvent createEvent() {
            // 레코드는 단순 데이터 객체이므로 new로 생성
            return new ShippingScheduledInternalEvent(
                    1L,   // eventId
                    444L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    222L, // shipmentId
                    300L, // bookId
                    "track-123" // trackingNo
            );
        }

        @Test
        @DisplayName("성공: ShippingStepService로 처리를 위임한다.")
        void onShippingScheduled_Success() {
            // given
            ShippingScheduledInternalEvent event = createEvent();

            doNothing().when(shippingStepService).afterShippingScheduled(any(ShippingScheduledInternalEvent.class));

            // when
            orchestrator.onShippingScheduled(event);

            // then
            // event 객체가 정확히 전달되었는지 검증
            verify(shippingStepService, times(1)).afterShippingScheduled(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onShippingScheduled_OptimisticLockFailure() {
            // given
            ShippingScheduledInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(shippingStepService).afterShippingScheduled(any(ShippingScheduledInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingScheduled(event));

            verify(shippingStepService, times(1)).afterShippingScheduled(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onShippingScheduled_GenericException() {
            // given
            ShippingScheduledInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(shippingStepService).afterShippingScheduled(any(ShippingScheduledInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onShippingScheduled(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);

            // 예외가 발생했더라도 shippingStepService.afterShippingScheduled가 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingScheduled(eq(event));
        }
    }

    @Nested
    @DisplayName("onShippingScheduleFailed: 배송 스케줄 실패 응답 처리")
    class OnShippingScheduleFailedTests {

        private ShippingScheduleFailedInternalEvent createEvent() {
            // 레코드는 단순 데이터 객체이므로 new로 생성
            return new ShippingScheduleFailedInternalEvent(
                    1L,   // eventId
                    555L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    "ADDR_ERR", // reasonCode
                    "주소 오류"  // message
            );
        }

        @Test
        @DisplayName("성공: ShippingStepService로 처리를 위임한다.")
        void onShippingScheduleFailed_Success() {
            // given
            ShippingScheduleFailedInternalEvent event = createEvent();

            doNothing().when(shippingStepService).afterShippingScheduleFailed(any(ShippingScheduleFailedInternalEvent.class));

            // when
            orchestrator.onShippingScheduleFailed(event);

            // then
            // event 객체가 정확히 전달되었는지 검증
            verify(shippingStepService, times(1)).afterShippingScheduleFailed(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onShippingScheduleFailed_OptimisticLockFailure() {
            // given
            ShippingScheduleFailedInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(shippingStepService).afterShippingScheduleFailed(any(ShippingScheduleFailedInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingScheduleFailed(event));

            // 예외가 발생해도 shippingStepService.afterShippingScheduleFailed 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingScheduleFailed(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onShippingScheduleFailed_GenericException() {
            // given
            ShippingScheduleFailedInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(shippingStepService).afterShippingScheduleFailed(any(ShippingScheduleFailedInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onShippingScheduleFailed(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);

            // 예외가 발생했더라도 shippingStepService.afterShippingScheduleFailed 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingScheduleFailed(eq(event));
        }
    }

    @Nested
    @DisplayName("onPointRefunded: 포인트 환불(보상) 응답 처리")
    class OnPointRefundedTests {

        private PointRefundedInternalEvent createEvent() {
            // 레코드는 단순 데이터 객체이므로 new로 생성
            return new PointRefundedInternalEvent(
                    1L,   // eventId
                    666L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    200L, // memberId
                    100L, // refundAmount
                    "refund-tx-123" // refundId (String)
            );
        }

        @Test
        @DisplayName("성공: PointStepService로 처리를 위임한다.")
        void onPointRefunded_Success() {
            // given
            PointRefundedInternalEvent event = createEvent();

            doNothing().when(pointStepService).afterPointRefunded(any(PointRefundedInternalEvent.class));

            // when
            orchestrator.onPointRefunded(event);

            // then
            // event 객체가 정확히 전달되었는지 검증
            verify(pointStepService, times(1)).afterPointRefunded(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onPointRefunded_OptimisticLockFailure() {
            // given
            PointRefundedInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(pointStepService).afterPointRefunded(any(PointRefundedInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onPointRefunded(event));

            // 예외가 발생했더라도 pointStepService.afterPointRefunded가 호출 시도되었는지 검증
            verify(pointStepService, times(1)).afterPointRefunded(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onPointRefunded_GenericException() {
            // given
            PointRefundedInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(pointStepService).afterPointRefunded(any(PointRefundedInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onPointRefunded(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);

            // 예외가 발생했더라도 pointStepService.afterPointRefunded 호출 시도되었는지 검증
            verify(pointStepService, times(1)).afterPointRefunded(eq(event));
        }
    }

    @Nested
    @DisplayName("onInventoryReleased: 재고 해제(보상) 응답 처리")
    class OnInventoryReleasedTests {

        private InventoryReleasedInternalEvent createEvent() {
            // 레코드는 단순 데이터 객체이므로 new로 생성
            return new InventoryReleasedInternalEvent(
                    1L,   // eventId
                    777L, // sagaId
                    2L,   // causationCommandId
                    0L,   // loanVersion
                    300L, // bookId
                    88L   // reservationId
            );
        }

        @Test
        @DisplayName("성공: InventoryStepService로 처리를 위임한다.")
        void onInventoryReleased_Success() {
            // given
            InventoryReleasedInternalEvent event = createEvent();

            doNothing().when(inventoryStepService).afterInventoryReleased(any(InventoryReleasedInternalEvent.class));

            // when
            orchestrator.onInventoryReleased(event);

            // then
            // event 객체가 정확히 전달되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReleased(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onInventoryReleased_OptimisticLockFailure() {
            // given
            InventoryReleasedInternalEvent event = createEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(inventoryStepService).afterInventoryReleased(any(InventoryReleasedInternalEvent.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReleased(event));

            // 예외가 발생해도 inventoryStepService.afterInventoryReleased 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReleased(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 다시 던진다.")
        void onInventoryReleased_GenericException() {
            // given
            InventoryReleasedInternalEvent event = createEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(inventoryStepService).afterInventoryReleased(any(InventoryReleasedInternalEvent.class));

            // when then
            assertThatThrownBy(() -> orchestrator.onInventoryReleased(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);

            // 예외가 발생해도 inventoryStepService.afterInventoryReleased 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReleased(eq(event));
        }
    }

    @Nested
    @DisplayName("requestCancel: 사가 취소 요청 처리")
    class RequestCancelTests {

        private static final long SAGA_ID = 123456L;
        private static final long LOAN_ID = 100L;
        private static final long MEMBER_ID = 200L;
        private static final long BOOK_ID = 300L;
        private static final long CAUSATION_ID = 400L;
        private static final SagaAbortReason REASON = SagaAbortReason.USER_CANCEL;
        private static final Duration COMPENSATION_TIMEOUT = Duration.ofMinutes(10);
        private static final long MOCK_CMD_ID = 12345L; // requestCancel에서 생성되는 커맨드 id

        @Mock
        private LoanSaga mockSaga;

        @BeforeEach
        void setUp() {
            lenient().when(sagaRepository.findForUpdate(eq(SAGA_ID))).thenReturn(Optional.of(mockSaga));

            lenient().when(sagaTimeouts.compensationTimeoutFor()).thenReturn(COMPENSATION_TIMEOUT);

            lenient().when(commandOutboxRecorder.save(any(SagaCommand.class))).thenReturn(true);

            lenient().when(sagaRepository.save(any(LoanSaga.class))).thenReturn(mockSaga);

            lenient().when(snowflake.nextId()).thenReturn(MOCK_CMD_ID);

            lenient().when(mockSaga.getId()).thenReturn(SAGA_ID);
            lenient().when(mockSaga.getLoanId()).thenReturn(LOAN_ID);
            lenient().when(mockSaga.getMemberId()).thenReturn(MEMBER_ID);
            lenient().when(mockSaga.getBookId()).thenReturn(BOOK_ID);
        }

        @Test
        @DisplayName("드롭: 사가 가드 로직(markCancelRequested)이 false를 반환하면 즉시 반환한다.")
        void requestCancel_DropWhenGuardFails() {
            // given
            when(mockSaga.markCancelRequested(REASON)).thenReturn(false);

            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(mockSaga, never()).markCancelled(any());
            verify(mockSaga, never()).enterCompensating(any(), any(LocalDateTime.class));
            verify(sagaRepository, never()).save(any());
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("즉시 취소(MEMBER_CHECKING): markCancelled 성공 시 save 및 clearSaga 호출")
        void requestCancel_ImmediateCancel_Success() {
            // given
            when(mockSaga.markCancelRequested(REASON)).thenReturn(true); // 상태가드 통과

            when(mockSaga.markCancelled(eq(REASON))).thenReturn(true); // 업데이트 성공
            when(mockSaga.getCurrentStep()).thenReturn(MEMBER_CHECKING);
            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(sagaRepository, times(1)).save(eq(mockSaga));
            verify(bookLoanRepository, times(1)).clearSagaIfMatches(eq(LOAN_ID), eq(SAGA_ID));
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("즉시 취소(MEMBER_CHECKING): markCancelled 실패(중복) 시 아무것도 안 함")
        void requestCancel_ImmediateCancel_Dropped() {
            // given
            when(mockSaga.markCancelRequested(REASON)).thenReturn(true);
            when(mockSaga.getCurrentStep()).thenReturn(LoanSagaStep.MEMBER_CHECKING);

            when(mockSaga.markCancelled(eq(REASON))).thenReturn(false); // 업데이트 실패

            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(sagaRepository, never()).save(any());
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyLong());
        }

        @Test
        @DisplayName("보상 시작(POINT_CHARGING): ReleaseInventory 커맨드를 발행한다.")
        void requestCancel_StartCompensation_ReleaseInventory() {
            // given
            when(mockSaga.markCancelRequested(REASON)).thenReturn(true);
            when(mockSaga.getCurrentStep()).thenReturn(LoanSagaStep.POINT_CHARGING);

            when(mockSaga.enterCompensating(eq(COMPENSATION_TIMEOUT), eq(FIXED_NOW))).thenReturn(true);

            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(sagaTimeouts, times(1)).compensationTimeoutFor();
            verify(sagaRepository, times(1)).save(eq(mockSaga));
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyLong()); // 즉시 취소 아님

            // Command 검증
            verify(commandOutboxRecorder, times(1)).save(releaseInventoryCommandCaptor.capture());
            ReleaseInventoryCommand captured = releaseInventoryCommandCaptor.getValue();

            assertThat(captured.commandId()).isEqualTo(MOCK_CMD_ID);
            assertThat(captured.sagaId()).isEqualTo(SAGA_ID);
            assertThat(captured.bookId()).isEqualTo(BOOK_ID);
            assertThat(captured.causationEventId()).isEqualTo(CAUSATION_ID);
            assertThat(captured.type()).isEqualTo(SagaCommandType.INVENTORY_RELEASE.getValue());
        }

        @Test
        @DisplayName("보상 시작(SHIPPING_SCHEDULING): RefundPoint 커맨드를 발행한다.")
        void requestCancel_StartCompensation_RefundPoint() {
            // given
            when(mockSaga.markCancelRequested(REASON)).thenReturn(true);
            when(mockSaga.getCurrentStep()).thenReturn(SHIPPING_SCHEDULING);

            when(mockSaga.enterCompensating(eq(COMPENSATION_TIMEOUT), eq(FIXED_NOW))).thenReturn(true);

            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(sagaTimeouts, times(1)).compensationTimeoutFor();
            verify(sagaRepository, times(1)).save(eq(mockSaga));

            // Command 검증
            verify(commandOutboxRecorder, times(1)).save(refundPointCommandCaptor.capture());
            RefundPointCommand captured = refundPointCommandCaptor.getValue();

            assertThat(captured.commandId()).isEqualTo(MOCK_CMD_ID);
            assertThat(captured.sagaId()).isEqualTo(SAGA_ID);
            assertThat(captured.memberId()).isEqualTo(MEMBER_ID);
            assertThat(captured.causationEventId()).isEqualTo(CAUSATION_ID);
            assertThat(captured.type()).isEqualTo(SagaCommandType.POINT_REFUND.getValue());
        }
    }
}