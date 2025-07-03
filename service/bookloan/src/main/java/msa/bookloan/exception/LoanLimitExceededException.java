package msa.bookloan.exception;

import msa.bookloan.exception.errorcode.BookLoanErrorCode;
import msa.common.exception.BusinessException;
import msa.common.exception.ErrorCode;

public class LoanLimitExceededException extends BusinessException {

    private static final ErrorCode errorCode = BookLoanErrorCode.LOAN_LIMIT_EXCEEDED;

    public LoanLimitExceededException() {
        super(errorCode);
    }
}
