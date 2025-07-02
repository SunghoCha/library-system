package msa.bookloan.exception;

import msa.bookloan.exception.errorcode.BookLoanErrorCode;
import msa.common.exception.BusinessException;
import msa.common.exception.ErrorCode;

public class LoanOverdueException extends BusinessException {

    private static final ErrorCode errorCode = BookLoanErrorCode.LOAN_OVERDUE;

    public LoanOverdueException() {
        super(errorCode);
    }
}
