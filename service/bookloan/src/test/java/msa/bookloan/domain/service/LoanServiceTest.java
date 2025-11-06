package msa.bookloan.domain.service;

import msa.bookloan.adapter.in.web.controller.dto.request.LoanCreateRequest;
import msa.bookloan.adapter.out.persistence.loan.BookLoanRepository;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.application.service.LoanService;
import msa.bookloan.application.service.LoanWriteService;
import msa.bookloan.application.service.dto.LoanCreateResult;
import msa.bookloan.application.service.exception.LoanLimitExceededException;
import msa.bookloan.domain.exception.OverdueBlockedException;
import msa.bookloan.domain.policy.LoanLimitPolicy;
import msa.common.domain.model.MemberGrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @InjectMocks
    private LoanService loanService;

    @Mock
    private BookLoanRepository bookLoanRepository;

    @Mock
    private MemberPort memberPort;

    @Mock
    private LoanLimitPolicy loanLimitPolicy;

    @Mock
    private LoanWriteService loanWriteService;

    @Mock
    private Clock clock;

    private static final LocalDate TODAY = LocalDate.of(2000, 11, 6);

    @BeforeEach
    void setUp() {
        Instant fixedInstant = TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant();
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    // TODO : 테스트 약식으로 한거라 보강 필요
    @Test
    @DisplayName("대출 생성: 성공 (연체 없고, 한도 여유)")
    void createLoan_success() {
        // given
        Long memberId = 1L;
        Long bookId = 100L;
        LoanCreateRequest request = new LoanCreateRequest(memberId, bookId);

        MemberGrade grade = MemberGrade.SILVER;
        int maxLoanCount = 5;
        long currentLoanCount = 2;

        LoanCreateResult expectedResult = new LoanCreateResult(999L, 888L);

        when(bookLoanRepository.countOverdue(eq(memberId), any(LocalDate.class)))
                .thenReturn(0L);

        when(memberPort.getGrade(memberId)).thenReturn(grade);
        when(loanLimitPolicy.maxLoansFor(grade)).thenReturn(maxLoanCount);
        when(bookLoanRepository.countActiveByMember(memberId)).thenReturn(currentLoanCount);

        when(loanWriteService.createAndPublish(memberId, bookId))
                .thenReturn(expectedResult);

        // when
        LoanCreateResult actualResult = loanService.createLoan(memberId, request);

        // then
        assertThat(actualResult).isEqualTo(expectedResult);

        verify(bookLoanRepository).countOverdue(memberId, TODAY);
        verify(memberPort).getGrade(memberId);
        verify(loanLimitPolicy).maxLoansFor(grade);
        verify(bookLoanRepository).countActiveByMember(memberId);
        verify(loanWriteService).createAndPublish(memberId, bookId);
    }

    @Test
    @DisplayName("대출 생성: 실패 (연체 중)")
    void createLoan_fail_whenOverdue() {
        // given
        Long memberId = 1L;
        Long bookId = 100L;
        LoanCreateRequest request = new LoanCreateRequest(memberId, bookId);

        when(bookLoanRepository.countOverdue(eq(memberId), any(LocalDate.class)))
                .thenReturn(1L);

        // when & then
        assertThatThrownBy(() -> loanService.createLoan(memberId, request))
                .isInstanceOf(OverdueBlockedException.class);

        verify(memberPort, never()).getGrade(anyLong());
        verify(loanLimitPolicy, never()).maxLoansFor(any());
        verify(loanWriteService, never()).createAndPublish(anyLong(), anyLong());
    }

    @Test
    @DisplayName("대출 생성: 실패 (대출 한도 초과)")
    void createLoan_fail_whenLimitExceeded() {
        // given
        Long memberId = 1L;
        Long bookId = 100L;
        LoanCreateRequest request = new LoanCreateRequest(memberId, bookId);

        MemberGrade grade = MemberGrade.SILVER;
        int maxLoanCount = 5;
        long currentLoanCount = 5;

        when(bookLoanRepository.countOverdue(eq(memberId), any(LocalDate.class)))
                .thenReturn(0L);

        when(memberPort.getGrade(memberId)).thenReturn(grade);
        when(loanLimitPolicy.maxLoansFor(grade)).thenReturn(maxLoanCount);
        when(bookLoanRepository.countActiveByMember(memberId)).thenReturn(currentLoanCount);

        // when & then
        assertThatThrownBy(() -> loanService.createLoan(memberId, request))
                .isInstanceOf(LoanLimitExceededException.class);

        verify(loanWriteService, never()).createAndPublish(anyLong(), anyLong());
    }


}