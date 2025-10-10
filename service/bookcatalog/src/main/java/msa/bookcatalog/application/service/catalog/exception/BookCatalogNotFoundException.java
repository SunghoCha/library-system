package msa.bookcatalog.application.service.catalog.exception;

import msa.bookcatalog.application.service.catalog.exception.errorcode.BookCatalogErrorCode;
import msa.common.exception.BusinessException;
import msa.common.exception.ErrorCode;

import java.util.Map;

public class BookCatalogNotFoundException extends BusinessException {

    private static final ErrorCode errorCode = BookCatalogErrorCode.OUTBOX_RECORD_NOT_FOUND;

    public BookCatalogNotFoundException() {
        super(errorCode);
    }

    public BookCatalogNotFoundException(Long eventId) {
        super(errorCode, Map.of("eventId", eventId));
    }
}
