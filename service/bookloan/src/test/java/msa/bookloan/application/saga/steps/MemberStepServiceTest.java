package msa.bookloan.application.saga.steps;

import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.outbox.CommandOutboxRecorder;
import msa.bookloan.adapter.out.persistence.saga.LoanSagaRepository;
import msa.bookloan.application.saga.SagaTimeouts;
import msa.bookloan.application.saga.command.ReserveInventoryCommand;
import msa.bookloan.application.saga.exception.SagaNotFoundException;
import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
import msa.bookloan.application.saga.reply.member.MemberCheckedPayload;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.SagaAbortReason;
import msa.bookloan.domain.saga.SagaStatus;
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

import java.time.Duration;
import java.util.Optional;

import static msa.bookloan.domain.saga.LoanSagaStep.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberStepServiceTest {

    @InjectMocks
    private MemberStepService memberStepService;

    @Mock
    private LoanSagaRepository sagaRepository;
    @Mock
    private CommandOutboxRecorder commandOutboxRecorder;
    @Mock
    private SagaTimeouts sagaTimeouts;
    @Mock
    private BookLoanRepository bookLoanRepository;
    @Mock
    private Snowflake snowflake;

    private LoanSaga testSaga;
    private final String SAGA_ID = "saga-member-123";
    private final Long LOAN_ID = 10L;
    private final Long MEMBER_ID = 100L;

    @BeforeEach
    void setUp() {
        testSaga = LoanSaga.startNew(SAGA_ID, LOAN_ID, MEMBER_ID, 200L, 0L, 99L);
        // 테스트 대상 단계인 MEMBER_CHECKING으로 상태 전이
        testSaga.markProcessing(MEMBER_CHECKING, Duration.ofMinutes(5));
    }

    @Nested
    @DisplayName("afterMemberChecked 메소드")
    class AfterMemberCheckedTest {

        @Test
        @DisplayName("성공(정상 멤버): 다음 단계(INVENTORY_RESERVING)로 전이하고 재고 예약 커맨드를 발행한다")
        void shouldTransitionToNextStep_whenMemberIsNotBlacklisted() {
            // given
            MemberCheckedPayload payload = new MemberCheckedPayload(MEMBER_ID, false, null);
            MemberCheckedInternalEvent event = new MemberCheckedInternalEvent(1L, SAGA_ID, 2L, 0L, payload);

            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));
            when(sagaTimeouts.stepTimeout(INVENTORY_RESERVING)).thenReturn(Duration.ofMinutes(5));
            when(snowflake.nextId()).thenReturn(12345L);

            // when
            memberStepService.afterMemberChecked(event);

            // then
            // 1. Saga 상태 검증
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository).saveAndFlush(sagaCaptor.capture());
            LoanSaga capturedSaga = sagaCaptor.getValue();
            assertThat(capturedSaga.getStatus()).isEqualTo(SagaStatus.PROCESSING);
            assertThat(capturedSaga.getCurrentStep()).isEqualTo(INVENTORY_RESERVING);

            // 2. Outbox Command 발행 검증
            ArgumentCaptor<ReserveInventoryCommand> commandCaptor = ArgumentCaptor.forClass(ReserveInventoryCommand.class);
            verify(commandOutboxRecorder).save(commandCaptor.capture());
            assertThat(commandCaptor.getValue().sagaId()).isEqualTo(SAGA_ID);

            // 3. BookLoan 정리 로직은 호출되지 않아야 함
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString());
        }

        @Test
        @DisplayName("성공(블랙리스트 멤버): 사가를 FAILED 상태로 전이시키고 BookLoan의 sagaId를 정리한다")
        void shouldTransitionToFailed_whenMemberIsBlacklisted() {
            // given
            MemberCheckedPayload payload = new MemberCheckedPayload(MEMBER_ID, true, "연체 이력");
            MemberCheckedInternalEvent event = new MemberCheckedInternalEvent(1L, SAGA_ID, 2L, 0L, payload);

            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            memberStepService.afterMemberChecked(event);

            // then
            // 1. Saga 상태 검증
            ArgumentCaptor<LoanSaga> sagaCaptor = ArgumentCaptor.forClass(LoanSaga.class);
            verify(sagaRepository).saveAndFlush(sagaCaptor.capture());
            LoanSaga capturedSaga = sagaCaptor.getValue();
            assertThat(capturedSaga.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(capturedSaga.getLastError()).isEqualTo(SagaAbortReason.BLACKLISTED.name());

            // 2. BookLoan 정리 로직 호출 검증
            verify(bookLoanRepository).clearSagaIfMatches(LOAN_ID, SAGA_ID);

            // 3. Outbox Command는 발행되지 않아야 함
            verify(commandOutboxRecorder, never()).save(any());
        }

        @Test
        @DisplayName("실패: 사가를 찾을 수 없으면 SagaNotFoundException을 던진다")
        void shouldThrowException_whenSagaNotFound() {
            // given
            MemberCheckedPayload payload = new MemberCheckedPayload(MEMBER_ID, false, null);
            MemberCheckedInternalEvent event = new MemberCheckedInternalEvent(1L, SAGA_ID, 2L, 0L, payload);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> memberStepService.afterMemberChecked(event))
                    .isInstanceOf(SagaNotFoundException.class);
        }

        @Test
        @DisplayName("무시: 사가가 이미 터미널 상태이면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsInTerminalState() {
            // given
            testSaga.markProcessing(SHIPPING_SCHEDULING, Duration.ofMinutes(5));
            testSaga.markCompleted(); // COMPLETED 상태로 설정
            MemberCheckedPayload payload = new MemberCheckedPayload(MEMBER_ID, false, null);
            MemberCheckedInternalEvent event = new MemberCheckedInternalEvent(1L, SAGA_ID, 2L, 0L, payload);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            memberStepService.afterMemberChecked(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(commandOutboxRecorder, never()).save(any());
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString());
        }

        @Test
        @DisplayName("무시: 사가가 올바른 단계(MEMBER_CHECKING)가 아니면 아무 작업도 수행하지 않는다")
        void shouldDoNothing_whenSagaIsInWrongStep() {
            // given
            testSaga.markProcessing(INVENTORY_RESERVING, Duration.ofMinutes(5)); // 다른 단계로 설정
            MemberCheckedPayload payload = new MemberCheckedPayload(MEMBER_ID, false, null);
            MemberCheckedInternalEvent event = new MemberCheckedInternalEvent(1L, SAGA_ID, 2L, 0L, payload);
            when(sagaRepository.findById(SAGA_ID)).thenReturn(Optional.of(testSaga));

            // when
            memberStepService.afterMemberChecked(event);

            // then
            verify(sagaRepository, never()).saveAndFlush(any());
            verify(commandOutboxRecorder, never()).save(any());
            verify(bookLoanRepository, never()).clearSagaIfMatches(anyLong(), anyString());
        }
    }
}