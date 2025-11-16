package msa.bookloan.application.service.exception;

import msa.bookloan.application.service.exception.errorcode.BookLoanErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class LoanOverdueException extends BusinessException {

    public LoanOverdueException(Long memberId) {
        super(BookLoanErrorCode.LOAN_OVERDUE, Map.of("memberId", memberId));
    }
}
