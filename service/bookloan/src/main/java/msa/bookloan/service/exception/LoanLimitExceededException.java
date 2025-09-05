package msa.bookloan.service.exception;

import msa.bookloan.service.exception.errorcode.BookLoanErrorCode;
import msa.common.exception.BusinessException;
import msa.common.exception.ErrorCode;

import java.util.Map;

public class LoanLimitExceededException extends BusinessException {

    private static final ErrorCode errorCode = BookLoanErrorCode.LOAN_LIMIT_EXCEEDED;

    public LoanLimitExceededException() {
        super(errorCode);
    }

    public LoanLimitExceededException(Long memberId) {
        super(errorCode, Map.of("memberId", memberId));
    }


}
