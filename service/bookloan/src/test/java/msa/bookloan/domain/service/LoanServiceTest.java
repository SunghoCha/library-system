package msa.bookloan.domain.service;

import msa.bookloan.adaptor.outbound.persistence.LoanRepository;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.dto.LoanCommand;
import msa.bookloan.exception.LoanOverdueException;
import msa.common.domain.MemberGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private LoanService loanService;

    private final Long MEMBER_ID = 1L;
    private final Long BOOK_ID = 42L;

    @Test
    @DisplayName("만약 회원이 연체 중인 도서가 하나라도 있으면 → 대출 거부")
    void test() {
        // given
        given(loanRepository.existsByMemberIdAndLoanStatus(MEMBER_ID, LoanStatus.OVERDUE))
                .willReturn(true);
        // when & then
        LoanCommand command = new LoanCommand(MEMBER_ID, MemberGrade.GOLD, BOOK_ID);
        assertThatThrownBy(() -> loanService.loanBook(command))
                .isInstanceOf(LoanOverdueException.class)
                .hasMessage("연체 중인 도서가 있습니다.");
        // 실제로 레파지토리가 조회가 한 번 호출됐는지 검증
        then(loanRepository).should(times(1))
                .existsByMemberIdAndLoanStatus(MEMBER_ID, LoanStatus.OVERDUE);
    }
}