package msa.bookloan.application.service.exception;

import msa.bookloan.application.service.exception.errorcode.BookLoanErrorCode;
import msa.common.exception.BusinessException;
import msa.common.exception.ErrorCode;

import java.util.Map;

public class LoanOverdueException extends BusinessException {

    private static final ErrorCode errorCode = BookLoanErrorCode.LOAN_OVERDUE;

    public LoanOverdueException() {
        super(errorCode);
    }

    public LoanOverdueException(Long memberId) {
        super(errorCode, Map.of("memberId", memberId));
    }
}
