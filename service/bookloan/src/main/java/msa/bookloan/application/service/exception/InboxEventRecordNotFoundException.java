package msa.bookloan.application.service.exception;

import msa.bookloan.application.service.exception.errorcode.BookLoanErrorCode;
import msa.common.exception.BusinessException;
import msa.common.exception.ErrorCode;

import java.util.Map;

public class InboxEventRecordNotFoundException extends BusinessException {

    private static final ErrorCode errorCode = BookLoanErrorCode.INBOX_RECORD_NOT_FOUND;

    public InboxEventRecordNotFoundException() {
        super(errorCode);
    }

    public InboxEventRecordNotFoundException(Long eventId) {
        super(errorCode, Map.of("eventId", eventId));
    }
}
