package msa.bookloan.domain.service;

import lombok.RequiredArgsConstructor;
import msa.bookloan.adaptor.outbound.persistence.LoanRepository;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.dto.LoanCommand;
import msa.bookloan.exception.LoanOverdueException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;

    public void loanBook(LoanCommand loanCommand) {
        /*
            대출가능한지 체크
            연체중인지/ 대출권수 초과인지
            대출체크에필요한 조건은 동기로 호출
       */
        // 대출 조건 체크. 그런데 연체조건이라는기 인터페이스 기반 전략인데..
        // 일단 간단한 구조부터 시도해보자. 처음부터 인터페이스 떠올리지말자
        // 가장 기본적인건 일단 여러 전략을 생각하지말고 당장의 연체조건만 체크. 다른 인터페이스없이 레파지토리에서 조회해서 조건체크.
        if (loanRepository.existsByMemberIdAndLoanStatus(loanCommand.memberId(), LoanStatus.OVERDUE)) {
            throw new LoanOverdueException();
        };
    }
}
