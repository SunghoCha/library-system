package msa.bookloan.application.saga;

import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.saga.command.*;
import msa.bookloan.application.saga.reply.inventory.InventoryReleasedReply;
import msa.bookloan.application.saga.reply.inventory.InventoryReserveFailedReply;
import msa.bookloan.application.saga.reply.inventory.InventoryReservedReply;
import msa.bookloan.application.saga.reply.member.MemberCheckedReply;
import msa.bookloan.application.saga.reply.point.PointChargeFailedReply;
import msa.bookloan.application.saga.reply.point.PointChargedReply;
import msa.bookloan.application.saga.reply.point.PointRefundedReply;
import msa.bookloan.application.saga.reply.shipping.ShippingAcceptedReply;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduleFailedReply;
import msa.bookloan.application.saga.reply.shipping.ShippingScheduledReply;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaAbortReason;
import msa.bookloan.domain.saga.SagaStatus;
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
import static org.assertj.core.api.Assertions.assertThat;
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
    private msa.bookloan.application.saga.steps.MemberStepService memberStepService;

    @Mock
    private msa.bookloan.application.saga.steps.InventoryStepService inventoryStepService;

    @Mock
    private msa.bookloan.application.saga.steps.PointStepService pointStepService;

    @Mock
    private msa.bookloan.application.saga.steps.ShippingStepService shippingStepService;

    @Captor
    private ArgumentCaptor<CheckMemberCommand> commandCaptor;

    @Captor
    private ArgumentCaptor<ReleaseInventoryCommand> releaseInventoryCommandCaptor;

    @Captor
    private ArgumentCaptor<RefundPointCommand> refundPointCommandCaptor;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2025, 10, 20, 10, 0, 0);
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

        private LoanRequestedInternalEvent createMockEvent() {
            LoanRequestedInternalEvent event = mock(LoanRequestedInternalEvent.class);
            lenient().when(event.loanId()).thenReturn(100L);
            lenient().when(event.sagaId()).thenReturn("saga-123");
            lenient().when(event.memberId()).thenReturn(200L);
            lenient().when(event.bookId()).thenReturn(300L);
            lenient().when(event.aggregateVersion()).thenReturn(1L);
            lenient().when(event.eventId()).thenReturn(900L);
            return event;
        }

        @Test
        @DisplayName("start 성공: 사가 바인딩 및 생성이 성공하고 멤버 확인 커맨드를 발행한다.")
        void start_Success() {
            // given
            LoanRequestedInternalEvent event = createMockEvent();
            String sagaId = event.sagaId();
            long loanId = event.loanId();
            long memberId = event.memberId();
            long bookId = event.bookId();
            long aggVer = event.aggregateVersion();
            long eventId = event.eventId();

            when(bookLoanRepository.tryBindSaga(anyLong(), anyString())).thenReturn(1);

            when(sagaRepository.insertIfAbsent(
                    anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
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
            assertThat(capturedCommand.type()).isEqualTo(SagaCommandType.MEMBER_CHECK);
        }

        @Test
        @DisplayName("start 실패: 바인딩 실패(tryBindSaga=0) 시 즉시 반환한다.")
        void start_FailsOnBind() {
            // given
            LoanRequestedInternalEvent event = createMockEvent();

            when(bookLoanRepository.tryBindSaga(anyLong(), anyString())).thenReturn(0);

            // when
            orchestrator.start(event);

            // then
            verify(bookLoanRepository).tryBindSaga(anyLong(), anyString());

            verify(sagaRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("start 실패: 사가 Row 중복(insertIfAbsent=false) 시 즉시 반환한다.")
        void start_FailsOnSagaCreate() {
            // given
            LoanRequestedInternalEvent event = createMockEvent();
            String sagaId = event.sagaId();
            long loanId = event.loanId();
            long memberId = event.memberId();
            long bookId = event.bookId();
            long aggVer = event.aggregateVersion();
            long eventId = event.eventId();

            when(bookLoanRepository.tryBindSaga(anyLong(), anyString())).thenReturn(1);

            when(sagaRepository.insertIfAbsent(
                    anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
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
            LoanRequestedInternalEvent event = createMockEvent();
            String sagaId = event.sagaId();
            long loanId = event.loanId();

            // 첫 번째 호출에는 1(성공) 반환, 두 번째 호출에는 0(실패) 반환
            when(bookLoanRepository.tryBindSaga(anyLong(), anyString()))
                    .thenReturn(1, 0);

            // 첫 번째 호출이 통과할 경우를 대비한 스터빙
            when(sagaRepository.insertIfAbsent(
                    anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
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
            LoanRequestedInternalEvent event = createMockEvent();
            String sagaId = event.sagaId();
            long loanId = event.loanId();

            // 1. tryBindSaga는 두 번 다 통과 (1 반환)
            when(bookLoanRepository.tryBindSaga(anyLong(), anyString()))
                    .thenReturn(1, 1);

            // 2. insertIfAbsent는 첫 번째는 성공(true), 두 번째는 실패(false)
            when(sagaRepository.insertIfAbsent(
                    anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
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

        private MemberCheckedReply createMockEvent() {
            MemberCheckedReply event = mock(MemberCheckedReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-123");
            return event;
        }

        @Test
        @DisplayName("성공: MemberStepService로 처리를 위임한다.")
        void onMemberChecked_Success() {
            // given
            MemberCheckedReply event = createMockEvent();

            doNothing().when(memberStepService).afterMemberChecked(any(MemberCheckedReply.class));

            // when
            orchestrator.onMemberChecked(event);

            // then
            verify(memberStepService, times(1)).afterMemberChecked(eq(event));
        }

        @Test
        @DisplayName("실패(낙관적 락): OptimisticLockingFailureException 발생 시 예외를 잡고 드롭시킨다.")
        void onMemberChecked_OptimisticLockFailure() {
            // given
            MemberCheckedReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(memberStepService).afterMemberChecked(any(MemberCheckedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onMemberChecked(event));

            verify(memberStepService, times(1)).afterMemberChecked(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onMemberChecked_GenericException() {
            // given
            MemberCheckedReply event = createMockEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(memberStepService).afterMemberChecked(any(MemberCheckedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onMemberChecked(event));

            verify(memberStepService, times(1)).afterMemberChecked(eq(event));
        }
    }

    @Nested
    @DisplayName("onInventoryReserved: 재고 예약 응답 처리")
    class OnInventoryReservedTests {

        private InventoryReservedReply createMockEvent() {
            InventoryReservedReply event = mock(InventoryReservedReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-456");
            return event;
        }

        @Test
        @DisplayName("성공: InventoryStepService로 처리를 위임한다.")
        void onInventoryReserved_Success() {
            // given
            InventoryReservedReply event = createMockEvent();

            doNothing().when(inventoryStepService).afterInventoryReserved(any(InventoryReservedReply.class));

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
            InventoryReservedReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(inventoryStepService).afterInventoryReserved(any(InventoryReservedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReserved(event));

            // 예외가 발생했더라도 inventoryStepService.afterInventoryReserved가 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserved(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onInventoryReserved_GenericException() {
            // given
            InventoryReservedReply event = createMockEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(inventoryStepService).afterInventoryReserved(any(InventoryReservedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReserved(event));

            // 예외가 발생했더라도 inventoryStepService.afterInventoryReserved가 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserved(eq(event));
        }
    }

    @Nested
    @DisplayName("onInventoryReserveFailed: 재고 예약 실패 응답 처리")
    class OnInventoryReserveFailedTests {

        private InventoryReserveFailedReply createMockEvent() {
            InventoryReserveFailedReply event = mock(InventoryReserveFailedReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-789-fail");
            return event;
        }

        @Test
        @DisplayName("성공: InventoryStepService로 처리를 위임한다.")
        void onInventoryReserveFailed_Success() {
            // given
            InventoryReserveFailedReply event = createMockEvent();

            doNothing().when(inventoryStepService).afterInventoryReserveFailed(any(InventoryReserveFailedReply.class));

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
            InventoryReserveFailedReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(inventoryStepService).afterInventoryReserveFailed(any(InventoryReserveFailedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReserveFailed(event));

            // 예외가 발생했더라도 inventoryStepService.afterInventoryReserveFailed가 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserveFailed(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onInventoryReserveFailed_GenericException() {
            // given
            InventoryReserveFailedReply event = createMockEvent();
            String errorMessage = "예상치 못한 에외";

            doThrow(new RuntimeException(errorMessage))
                    .when(inventoryStepService).afterInventoryReserveFailed(any(InventoryReserveFailedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReserveFailed(event));

            // 예외가 발생했더라도 inventoryStepService.afterInventoryReserveFailed가 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReserveFailed(eq(event));
        }

        @Nested
        @DisplayName("onPointCharged: 포인트 차감 응답 처리")
        class OnPointChargedTests {

            private PointChargedReply createMockEvent() {
                PointChargedReply event = mock(PointChargedReply.class);
                lenient().when(event.sagaId()).thenReturn("saga-point-charged-111");
                return event;
            }

            @Test
            @DisplayName("성공: PointStepService로 처리를 위임한다.")
            void onPointCharged_Success() {
                // given
                PointChargedReply event = createMockEvent();

                doNothing().when(pointStepService).afterPointCharged(any(PointChargedReply.class));

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
                PointChargedReply event = createMockEvent();
                String errorMessage = "사가 버전 불일치";

                doThrow(new OptimisticLockingFailureException(errorMessage))
                        .when(pointStepService).afterPointCharged(any(PointChargedReply.class));

                // when then
                assertDoesNotThrow(() -> orchestrator.onPointCharged(event));

                // 예외가 발생했더라도 pointStepService.afterPointCharged가 호출 시도되었는지 검증
                verify(pointStepService, times(1)).afterPointCharged(eq(event));
            }

            @Test
            @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
            void onPointCharged_GenericException() {
                // given
                PointChargedReply event = createMockEvent();
                String errorMessage = "예상치 못한 예외";

                doThrow(new RuntimeException(errorMessage))
                        .when(pointStepService).afterPointCharged(any(PointChargedReply.class));

                // when then
                assertDoesNotThrow(() -> orchestrator.onPointCharged(event));

                // 예외가 발생했더라도 pointStepService.afterPointCharged가 호출 시도되었는지 검증
                verify(pointStepService, times(1)).afterPointCharged(eq(event));
            }
        } 
    }

    @Nested
    @DisplayName("onPointChargeFailed: 포인트 차감 실패 응답 처리")
    class OnPointChargeFailedTests {

        private PointChargeFailedReply createMockEvent() {
            PointChargeFailedReply event = mock(PointChargeFailedReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-point-failed-222");
            return event;
        }

        @Test
        @DisplayName("성공: PointStepService로 처리를 위임한다.")
        void onPointChargeFailed_Success() {
            // given
            PointChargeFailedReply event = createMockEvent();

            doNothing().when(pointStepService).afterPointChargeFailed(any(PointChargeFailedReply.class));

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
            PointChargeFailedReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(pointStepService).afterPointChargeFailed(any(PointChargeFailedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onPointChargeFailed(event));

            // 예외가 발생했더라도 pointStepService.afterPointChargeFailed가 호출 시도되었는지 검증
            verify(pointStepService, times(1)).afterPointChargeFailed(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onPointChargeFailed_GenericException() {
            // given
            PointChargeFailedReply event = createMockEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(pointStepService).afterPointChargeFailed(any(PointChargeFailedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onPointChargeFailed(event));

            // 예외가 발생했더라도 pointStepService.afterPointChargeFailed가 호출 시도되었는지 검증
            verify(pointStepService, times(1)).afterPointChargeFailed(eq(event));
        }
    }

    @Nested
    @DisplayName("onShippingAccepted: 배송 수락 응답 처리")
    class OnShippingAcceptedTests {

        private ShippingAcceptedReply createMockEvent() {
            ShippingAcceptedReply event = mock(ShippingAcceptedReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-shipping-accepted");
            return event;
        }

        @Test
        @DisplayName("성공: ShippingStepService로 처리를 위임한다.")
        void onShippingAccepted_Success() {
            // given
            ShippingAcceptedReply event = createMockEvent();

            doNothing().when(shippingStepService).afterShippingAccepted(any(ShippingAcceptedReply.class));

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
            ShippingAcceptedReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(shippingStepService).afterShippingAccepted(any(ShippingAcceptedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingAccepted(event));

            // 예외가 발생했더라도 shippingStepService.afterShippingAccepted 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingAccepted(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onShippingAccepted_GenericException() {
            // given
            ShippingAcceptedReply event = createMockEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(shippingStepService).afterShippingAccepted(any(ShippingAcceptedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingAccepted(event));

            // 예외가 발생했더라도 shippingStepService.afterShippingAccepted 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingAccepted(eq(event));
        }
    }

    @Nested
    @DisplayName("onShippingScheduled: 배송 스케줄 성공 응답 처리")
    class OnShippingScheduledTests {

        private ShippingScheduledReply createMockEvent() {
            ShippingScheduledReply event = mock(ShippingScheduledReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-shipping-scheduled");
            return event;
        }

        @Test
        @DisplayName("성공: ShippingStepService로 처리를 위임한다.")
        void onShippingScheduled_Success() {
            // given
            ShippingScheduledReply event = createMockEvent();

            doNothing().when(shippingStepService).afterShippingScheduled(any(ShippingScheduledReply.class));

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
            ShippingScheduledReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(shippingStepService).afterShippingScheduled(any(ShippingScheduledReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingScheduled(event));

            verify(shippingStepService, times(1)).afterShippingScheduled(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onShippingScheduled_GenericException() {
            // given
            ShippingScheduledReply event = createMockEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(shippingStepService).afterShippingScheduled(any(ShippingScheduledReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingScheduled(event));

            // 예외가 발생했더라도 shippingStepService.afterShippingScheduled가 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingScheduled(eq(event));
        }
    }

    @Nested
    @DisplayName("onShippingScheduleFailed: 배송 스케줄 실패 응답 처리")
    class OnShippingScheduleFailedTests {

        private ShippingScheduleFailedReply createMockEvent() {
            ShippingScheduleFailedReply event = mock(ShippingScheduleFailedReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-shipping-failed");
            return event;
        }

        @Test
        @DisplayName("성공: ShippingStepService로 처리를 위임한다.")
        void onShippingScheduleFailed_Success() {
            // given
            ShippingScheduleFailedReply event = createMockEvent();

            doNothing().when(shippingStepService).afterShippingScheduleFailed(any(ShippingScheduleFailedReply.class));

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
            ShippingScheduleFailedReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(shippingStepService).afterShippingScheduleFailed(any(ShippingScheduleFailedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingScheduleFailed(event));

            // 예외가 발생해도 shippingStepService.afterShippingScheduleFailed 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingScheduleFailed(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onShippingScheduleFailed_GenericException() {
            // given
            ShippingScheduleFailedReply event = createMockEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(shippingStepService).afterShippingScheduleFailed(any(ShippingScheduleFailedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onShippingScheduleFailed(event));

            // 예외가 발생했더라도 shippingStepService.afterShippingScheduleFailed 호출 시도되었는지 검증
            verify(shippingStepService, times(1)).afterShippingScheduleFailed(eq(event));
        }
    }

    @Nested
    @DisplayName("onPointRefunded: 포인트 환불(보상) 응답 처리")
    class OnPointRefundedTests {

        private PointRefundedReply createMockEvent() {
            PointRefundedReply event = mock(PointRefundedReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-point-refunded");
            return event;
        }

        @Test
        @DisplayName("성공: PointStepService로 처리를 위임한다.")
        void onPointRefunded_Success() {
            // given
            PointRefundedReply event = createMockEvent();

            doNothing().when(pointStepService).afterPointRefunded(any(PointRefundedReply.class));

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
            PointRefundedReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(pointStepService).afterPointRefunded(any(PointRefundedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onPointRefunded(event));

            // 예외가 발생했더라도 pointStepService.afterPointRefunded가 호출 시도되었는지 검증
            verify(pointStepService, times(1)).afterPointRefunded(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onPointRefunded_GenericException() {
            // given
            PointRefundedReply event = createMockEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(pointStepService).afterPointRefunded(any(PointRefundedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onPointRefunded(event));

            // 예외가 발생했더라도 pointStepService.afterPointRefunded 호출 시도되었는지 검증
            verify(pointStepService, times(1)).afterPointRefunded(eq(event));
        }
    }

    @Nested
    @DisplayName("onInventoryReleased: 재고 해제(보상) 응답 처리")
    class OnInventoryReleasedTests {

        private InventoryReleasedReply createMockEvent() {
            InventoryReleasedReply event = mock(InventoryReleasedReply.class);
            lenient().when(event.sagaId()).thenReturn("saga-inventory-released");
            return event;
        }

        @Test
        @DisplayName("성공: InventoryStepService로 처리를 위임한다.")
        void onInventoryReleased_Success() {
            // given
            InventoryReleasedReply event = createMockEvent();

            doNothing().when(inventoryStepService).afterInventoryReleased(any(InventoryReleasedReply.class));

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
            InventoryReleasedReply event = createMockEvent();
            String errorMessage = "사가 버전 불일치";

            doThrow(new OptimisticLockingFailureException(errorMessage))
                    .when(inventoryStepService).afterInventoryReleased(any(InventoryReleasedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReleased(event));

            // 예외가 발생해도 inventoryStepService.afterInventoryReleased 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReleased(eq(event));
        }

        @Test
        @DisplayName("실패(기타 예외): 예상치 못한 예외 발생 시 예외를 잡고 경고 로그를 남긴다.")
        void onInventoryReleased_GenericException() {
            // given
            InventoryReleasedReply event = createMockEvent();
            String errorMessage = "예상치 못한 예외";

            doThrow(new RuntimeException(errorMessage))
                    .when(inventoryStepService).afterInventoryReleased(any(InventoryReleasedReply.class));

            // when then
            assertDoesNotThrow(() -> orchestrator.onInventoryReleased(event));

            // 예외가 발생해도 inventoryStepService.afterInventoryReleased 호출 시도되었는지 검증
            verify(inventoryStepService, times(1)).afterInventoryReleased(eq(event));
        }
    }

    @Nested
    @DisplayName("requestCancel: 사가 취소 요청 처리")
    class RequestCancelTests {

        private static final String SAGA_ID = "saga-cancel-123";
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
            lenient().when(sagaRepository.findById(eq(SAGA_ID))).thenReturn(Optional.of(mockSaga));

            lenient().when(sagaTimeouts.compensationTimeoutFor()).thenReturn(COMPENSATION_TIMEOUT);

            lenient().when(commandOutboxRecorder.save(any(SagaCommand.class))).thenReturn(true);

            lenient().when(sagaRepository.save(any(LoanSaga.class))).thenReturn(mockSaga);

            lenient().when(snowflake.nextId()).thenReturn(MOCK_CMD_ID);

            lenient().when(mockSaga.getSagaId()).thenReturn(SAGA_ID);
            lenient().when(mockSaga.getLoanId()).thenReturn(LOAN_ID);
            lenient().when(mockSaga.getMemberId()).thenReturn(MEMBER_ID);
            lenient().when(mockSaga.getBookId()).thenReturn(BOOK_ID);
        }

        @Test
        @DisplayName("드롭: 사가가 이미 Terminal 상태면 즉시 반환한다.")
        void requestCancel_DropWhenTerminal() {
            // given
            when(mockSaga.isTerminal()).thenReturn(true);

            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(mockSaga, never()).markCancelled(any());
            verify(mockSaga, never()).enterCompensating(any(), any(LocalDateTime.class));
            verify(sagaRepository, never()).save(any());
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("드롭: 사가가 이미 Pivot 이후면 즉시 반환한다.")
        void requestCancel_DropWhenAfterPivot() {
            // given
            when(mockSaga.isTerminal()).thenReturn(false);
            when(mockSaga.isAfterPivot()).thenReturn(true);

            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(mockSaga, never()).markCancelled(any());
            verify(mockSaga, never()).enterCompensating(any(), any(LocalDateTime.class));
            verify(sagaRepository, never()).save(any());
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("드롭: 사가가 이미 보상(COMPENSATING) 상태면 즉시 반환한다.")
        void requestCancel_DropWhenCompensating() {
            // given
            when(mockSaga.isTerminal()).thenReturn(false);
            when(mockSaga.isAfterPivot()).thenReturn(false);
            when(mockSaga.getStatus()).thenReturn(SagaStatus.COMPENSATING);

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
            when(mockSaga.isTerminal()).thenReturn(false);
            when(mockSaga.isAfterPivot()).thenReturn(false);
            when(mockSaga.getStatus()).thenReturn(SagaStatus.PROCESSING);
            when(mockSaga.getCurrentStep()).thenReturn(LoanSagaStep.MEMBER_CHECKING); // 상태가드 통과

            when(mockSaga.markCancelled(eq(REASON))).thenReturn(true); // 업데이트 성공

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
            when(mockSaga.isTerminal()).thenReturn(false);
            when(mockSaga.isAfterPivot()).thenReturn(false);
            when(mockSaga.getStatus()).thenReturn(SagaStatus.PROCESSING);
            when(mockSaga.getCurrentStep()).thenReturn(LoanSagaStep.MEMBER_CHECKING);

            when(mockSaga.markCancelled(eq(REASON))).thenReturn(false); // 업데이트 실패

            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(sagaRepository, never()).save(any());
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString());
        }

        @Test
        @DisplayName("보상 시작(POINT_CHARGING): ReleaseInventory 커맨드를 발행한다.")
        void requestCancel_StartCompensation_ReleaseInventory() {
            // given
            when(mockSaga.isTerminal()).thenReturn(false);
            when(mockSaga.isAfterPivot()).thenReturn(false);
            when(mockSaga.getStatus()).thenReturn(SagaStatus.PROCESSING);
            when(mockSaga.getCurrentStep()).thenReturn(LoanSagaStep.POINT_CHARGING);

            when(mockSaga.enterCompensating(eq(COMPENSATION_TIMEOUT),eq(FIXED_NOW))).thenReturn(true);

            // when
            orchestrator.requestCancel(SAGA_ID, REASON, CAUSATION_ID);

            // then
            verify(sagaTimeouts, times(1)).compensationTimeoutFor();
            verify(sagaRepository, times(1)).save(eq(mockSaga));
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString()); // 즉시 취소 아님

            // Command 검증
            verify(commandOutboxRecorder, times(1)).save(releaseInventoryCommandCaptor.capture());
            ReleaseInventoryCommand captured = releaseInventoryCommandCaptor.getValue();

            assertThat(captured.commandId()).isEqualTo(MOCK_CMD_ID);
            assertThat(captured.sagaId()).isEqualTo(SAGA_ID);
            assertThat(captured.bookId()).isEqualTo(BOOK_ID);
            assertThat(captured.causationEventId()).isEqualTo(CAUSATION_ID);
            assertThat(captured.type()).isEqualTo(SagaCommandType.INVENTORY_RELEASE);
        }

        @Test
        @DisplayName("보상 시작(SHIPPING_SCHEDULING): RefundPoint 커맨드를 발행한다.")
        void requestCancel_StartCompensation_RefundPoint() {
            // given
            when(mockSaga.isTerminal()).thenReturn(false);
            when(mockSaga.isAfterPivot()).thenReturn(false);
            when(mockSaga.getStatus()).thenReturn(SagaStatus.PROCESSING);
            when(mockSaga.getCurrentStep()).thenReturn(LoanSagaStep.SHIPPING_SCHEDULING);

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
            assertThat(captured.type()).isEqualTo(SagaCommandType.POINT_REFUND);
        }
    }
}