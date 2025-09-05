package msa.bookcatalog.service.exception.errorcode;

import msa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BookCatalogErrorCode implements ErrorCode {
    BOOK_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "C-001", "이미 등록된 도서입니다."),

    OUTBOX_RECORD_NOT_FOUND(HttpStatus.BAD_REQUEST, "O-001", "아웃박스 레코드를 찾을 수 없습니다."),
    BOOK_CATALOG_NOT_FOUND(HttpStatus.BAD_REQUEST, "O-001", "북카탈로그를 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

    BookCatalogErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
