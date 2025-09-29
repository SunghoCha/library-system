package msa.bookcatalog.service.exception;

import msa.bookcatalog.service.exception.errorcode.BookCatalogErrorCode;
import msa.common.exception.BusinessException;
import msa.common.exception.ErrorCode;

import java.util.Map;

public class DuplicateBookException extends BusinessException {

    private static final ErrorCode errorCode = BookCatalogErrorCode.BOOK_ALREADY_EXISTS;

    public DuplicateBookException() {
        super(errorCode);
    }

    public DuplicateBookException(String isbn13) {
        super(errorCode, Map.of("isbn13", isbn13));
    }


}
