package msa.bookloan.application.service.exception;

import msa.bookloan.domain.exception.errorCode.BookLoanErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class LoanNotCancellableException extends BusinessException {

    public LoanNotCancellableException(Long loanId) {
        super(BookLoanErrorCode.LOAN_NOT_CANCELLABLE, Map.of("loanId", loanId));
    }
}
