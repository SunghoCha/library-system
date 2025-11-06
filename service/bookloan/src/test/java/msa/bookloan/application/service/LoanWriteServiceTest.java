package msa.bookloan.application.service;

import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.adapter.out.persistence.projection.entity.BookCatalogProjection;
import msa.bookloan.adapter.out.persistence.projection.repository.BookCatalogProjectionRepository;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.application.service.dto.LoanCreateResult;
import msa.bookloan.domain.model.BookLoan;
import msa.bookloan.domain.model.BookType;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.policy.LoanTermPolicy;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanWriteServiceTest {

    @InjectMocks
    private LoanWriteService loanWriteService;

    @Mock
    private BookLoanRepository bookLoanRepository;

    @Mock
    private BookCatalogProjectionRepository projectionRepository;

    @Mock
    private LoanTermPolicy loanTermPolicy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Snowflake snowflake;

    @Mock
    private Clock clock;

    private static final LocalDate TODAY = LocalDate.of(2025, 11, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2025, 11, 6, 15, 30, 0);

    @BeforeEach
    void setUp() {
        // Clock 고정
        Instant fixedInstant = NOW.atZone(ZoneId.systemDefault()).toInstant();
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    @DisplayName("도서 대출(Saga 시작) 성공 - 도서 정보를 찾음")
    @Test
    void createAndPublish_success_whenBookFound() {
        // given
        Long memberId = 1L;
        Long bookId = 100L;
        long expectedLoanId = 1000L;
        long expectedSagaId = 2000L;
        long expectedEventId = 3000L;
        long expectedVersion = 0L;
        long loanTermDays = 14L;
        BookType expectedBookType = BookType.STANDARD;

        when(snowflake.nextId())
                .thenReturn(expectedLoanId)   // loanId
                .thenReturn(expectedSagaId)   // sagaId
                .thenReturn(expectedEventId); // eventId

        BookCatalogProjection mockProjection = mock(BookCatalogProjection.class);
        when(mockProjection.getBookType()).thenReturn("STANDARD");
        when(projectionRepository.findById(bookId)).thenReturn(Optional.of(mockProjection));

        when(loanTermPolicy.loanPeriodFor(expectedBookType)).thenReturn(loanTermDays);

        BookLoan mockSavedLoan = mock(BookLoan.class);
        when(bookLoanRepository.saveAndFlush(any(BookLoan.class))).thenReturn(mockSavedLoan);

        ArgumentCaptor<BookLoan> loanCaptor = ArgumentCaptor.forClass(BookLoan.class);
        ArgumentCaptor<LoanRequestedInternalEvent> eventCaptor = ArgumentCaptor.forClass(LoanRequestedInternalEvent.class);

        // when
        LoanCreateResult result = loanWriteService.createAndPublish(memberId, bookId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.loanId()).isEqualTo(expectedLoanId);
        assertThat(result.sagaId()).isEqualTo(expectedSagaId);

        verify(bookLoanRepository).saveAndFlush(loanCaptor.capture());
        BookLoan capturedLoan = loanCaptor.getValue();

        assertThat(capturedLoan.getId()).isEqualTo(expectedLoanId);
        assertThat(capturedLoan.getMemberId()).isEqualTo(memberId);
        assertThat(capturedLoan.getBookId()).isEqualTo(bookId);
        assertThat(capturedLoan.getCurrentSagaId()).isEqualTo(expectedSagaId);
        assertThat(capturedLoan.getLoanStatus()).isEqualTo(LoanStatus.PENDING);
        assertThat(capturedLoan.getLoanDate()).isEqualTo(TODAY);
        assertThat(capturedLoan.getDueDate()).isEqualTo(TODAY.plusDays(loanTermDays));

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        LoanRequestedInternalEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.sagaId()).isEqualTo(expectedSagaId);
        assertThat(capturedEvent.loanId()).isEqualTo(expectedLoanId);
        assertThat(capturedEvent.memberId()).isEqualTo(memberId);
        assertThat(capturedEvent.bookId()).isEqualTo(bookId);
        assertThat(capturedEvent.eventId()).isEqualTo(expectedEventId);
        assertThat(capturedEvent.occurredAt()).isEqualTo(NOW);
    }

    @DisplayName("도서 대출(Saga 시작) 성공 - 도서 정보를 못찾음 (UNKNOWN 타입)")
    @Test
    void createAndPublish_success_whenBookNotFound() {
        // given
        Long memberId = 1L;
        Long bookId = 100L;
        long loanTermDaysUnknown = 7L; // UNKNOWN 타입의 대출 기간

        when(snowflake.nextId()).thenReturn(1001L, 2001L, 3001L);

        when(projectionRepository.findById(bookId)).thenReturn(Optional.empty());

        when(loanTermPolicy.loanPeriodFor(BookType.UNKNOWN)).thenReturn(loanTermDaysUnknown);

        BookLoan mockSavedLoan = mock(BookLoan.class);
        when(bookLoanRepository.saveAndFlush(any(BookLoan.class))).thenReturn(mockSavedLoan);

        ArgumentCaptor<BookLoan> loanCaptor = ArgumentCaptor.forClass(BookLoan.class);

        // when
        loanWriteService.createAndPublish(memberId, bookId);

        // then
        verify(bookLoanRepository).saveAndFlush(loanCaptor.capture());
        BookLoan capturedLoan = loanCaptor.getValue();

        assertThat(capturedLoan.getDueDate()).isEqualTo(TODAY.plusDays(loanTermDaysUnknown));

        verify(eventPublisher).publishEvent(any(LoanRequestedInternalEvent.class));
    }
}