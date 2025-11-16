package msa.bookloan.application.service.exception;

import msa.bookloan.application.service.exception.errorcode.BookLoanErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class LoanLimitExceededException extends BusinessException {

    public LoanLimitExceededException(Long memberId) {
        super(BookLoanErrorCode.LOAN_LIMIT_EXCEEDED, Map.of("memberId", memberId));
    }


}
