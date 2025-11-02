package msa.bookloan.application.saga.steps;

import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.saga.reply.inventory.InventoryReleasedInternalEvent;
import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.LoanProcessStatus;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.bookloan.testsupport.time.TestClocks;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Transactional
@SpringBootTest
public class InventoryStepServiceIntegrationTest {

    @Autowired
    private InventoryStepService inventoryStepService;

    @Autowired
    private LoanSagaRepository sagaRepository;

    @Autowired
    private BookLoanRepository bookLoanRepository;

    @Autowired
    private Snowflake snowflake;

    private final Clock fixedClock = TestClocks.FIXED_CLOCK;

    private Long SAGA_ID;
    private Long LOAN_ID;
    private Long BOOK_ID = 99L;
    private Long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        // 매 테스트마다 고유한 ID 사용
        SAGA_ID = snowflake.nextId();
        LOAN_ID = snowflake.nextId();

        bookLoanRepository.deleteAllInBatch();
        sagaRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("afterInventoryReleased 메소드 (재고 해제 보상 완료 시)")
    class AfterInventoryReleasedTest {

        @Test
        @DisplayName("성공: 보상 중인 사가를 FAILED(COMPENSATION)로 확정하고 BookLoan의 sagaId를 정리한다")
        void shouldFinalizeAsFailed_whenCompensating() {
            // given
            // 시맨틱 락 걸린 bookLoan
            setupBookLoanInDb(LOAN_ID, BOOK_ID);

            // when


            // then
        }

    }


    private LoanSaga setupSagaInDb(Long sagaId, Long loanId, SagaStatus status, LoanSagaStep step) {
        LoanSaga saga = LoanSaga.builder()
                .id(sagaId)
                .loanId(loanId)
                .memberId(MEMBER_ID)
                .bookId(BOOK_ID)
                .aggregateVersion(0L)
                .triggerEventId(snowflake.nextId()) // 임의값
                .status(status)
                .currentStep(step)
                .stepDeadlineAt(LocalDateTime.now().plusMinutes(5))
                .build();
        return sagaRepository.saveAndFlush(saga);
    }

    private BookLoan setupBookLoanInDb(Long loanId, Long sagaId) {
        BookLoan bookLoan = BookLoan.builder()
                .id(loanId)
                .memberId(MEMBER_ID)
                .bookId(BOOK_ID)
                .loanStatus(LoanStatus.PENDING)
                .currentSagaId(sagaId) // 시맨틱 락
                .build();
        return bookLoanRepository.saveAndFlush(bookLoan);
    }

    private InventoryReleasedInternalEvent createReleasedEvent(Long sagaId) {
        return new InventoryReleasedInternalEvent(
                snowflake.nextId(),
                sagaId,
                snowflake.nextId(),
                0L, // loanVersion
                BOOK_ID,
                snowflake.nextId() // reservationId
        );
    }
}
