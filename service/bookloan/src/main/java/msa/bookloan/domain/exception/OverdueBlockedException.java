package msa.bookloan.domain.exception;

import msa.bookloan.domain.exception.errorCode.BookLoanErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class OverdueBlockedException extends BusinessException {
    public OverdueBlockedException(Long memberId, long overdueCount) {
        super(BookLoanErrorCode.OVERDUE_BLOCKED,
                Map.of("memberId", memberId, "overdueCount", overdueCount));
    }
}
