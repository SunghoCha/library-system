package msa.bookcatalog.service.exception;

import msa.bookcatalog.service.exception.errorcode.BookCatalogErrorCode;
import msa.common.exception.BusinessException;
import msa.common.exception.ErrorCode;

import java.util.Map;

public class OutboxEventRecordNotFoundException extends BusinessException {

    private static final ErrorCode errorCode = BookCatalogErrorCode.OUTBOX_RECORD_NOT_FOUND;

    public OutboxEventRecordNotFoundException() {
        super(errorCode);
    }

    public OutboxEventRecordNotFoundException(Long eventId) {
        super(errorCode, Map.of("eventId", eventId));
    }
}
