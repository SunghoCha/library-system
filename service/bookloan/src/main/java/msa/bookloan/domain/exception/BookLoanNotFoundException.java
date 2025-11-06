package msa.bookloan.domain.exception;

import msa.bookloan.domain.exception.errorCode.BookLoanErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class BookLoanNotFoundException extends BusinessException {
    public BookLoanNotFoundException(Long loanId) {
        super(BookLoanErrorCode.LOAN_NOT_FOUND, Map.of("loanId", loanId));
    }
}