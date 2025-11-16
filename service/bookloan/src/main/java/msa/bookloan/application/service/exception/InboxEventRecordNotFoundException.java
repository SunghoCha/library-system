package msa.bookloan.application.service.exception;

import msa.bookloan.application.service.exception.errorcode.BookLoanErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class InboxEventRecordNotFoundException extends BusinessException {

    public InboxEventRecordNotFoundException(Long eventId) {
        super(BookLoanErrorCode.INBOX_RECORD_NOT_FOUND, Map.of("eventId", eventId));
    }
}
