package msa.bookloan.domain.service;

import msa.bookloan.adaptor.outbound.persistence.LoanRepository;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.policy.LoanLimitPolicy;
import msa.bookloan.domain.policy.rule.LoanValidationRule;
import msa.bookloan.dto.LoanCommand;
import msa.bookloan.exception.LoanLimitExceededException;
import msa.bookloan.exception.LoanOverdueException;
import msa.common.domain.MemberGrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanValidationRule overdueRule;

    @Mock
    private LoanValidationRule loanLimitRule;

    @Mock
    private LoanLimitPolicy loanLimitPolicy;

    @InjectMocks
    private LoanService loanService;

    private final Long MEMBER_ID = 1L;
    private final Long BOOK_ID = 42L;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(
                loanRepository,
                List.of(overdueRule, loanLimitRule),
                loanLimitPolicy
        );
    }

    @Test
    @DisplayName("조건을 만족하면 대출 승인")
    void loanBook_with_correct_input() {
        // given
        given(loanRepository.existsByMemberIdAndLoanStatus(MEMBER_ID, LoanStatus.OVERDUE))
                .willReturn(false);
        // when

        // then
    }

    @Test
    @DisplayName("회원이 연체 중인 도서가 하나라도 있으면 대출 거부")
    void loanBook_with_wrong_input_about_overdue() {
        // given
        doThrow(new LoanOverdueException())
                .when(overdueRule)
                .validate(any());
        // when & then
        LoanCommand command = new LoanCommand(MEMBER_ID, MemberGrade.GOLD, BOOK_ID);
        assertThatThrownBy(() -> loanService.loanBooks(command))
                .isInstanceOf(LoanOverdueException.class)
                .hasMessage("연체 중인 도서가 있습니다.");

        then(overdueRule).should(times(1)).validate(any());
    }

    @Test
    @DisplayName("회원이 대출한도를 초과했으면 대출 거부")
    void loanBook_with_wrong_input_about_loan_limit() {
        // given
        doThrow(new LoanLimitExceededException())
                .when(loanLimitRule)
                .validate(any());
        // when & then
        LoanCommand command = new LoanCommand(MEMBER_ID, MemberGrade.GOLD, BOOK_ID);
        assertThatThrownBy(() -> loanService.loanBooks(command))
                .isInstanceOf(LoanLimitExceededException.class)
                .hasMessage("대출 한도를 초과했습니다.");

        then(loanLimitRule).should(times(1)).validate(any());
    }


}