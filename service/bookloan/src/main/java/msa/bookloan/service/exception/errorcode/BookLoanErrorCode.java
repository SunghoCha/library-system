package msa.bookloan.service.exception.errorcode;

import msa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BookLoanErrorCode implements ErrorCode {
    LOAN_NOT_FOUND(HttpStatus.NOT_FOUND, "L-001", "대출된 도서를 찾을 수 없습니다."),
    LOAN_OVERDUE(HttpStatus.BAD_REQUEST, "L-002", "연체 중인 도서가 있습니다."),
    LOAN_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "L-003", "대출 한도를 초과했습니다."),

    INBOX_RECORD_NOT_FOUND(HttpStatus.BAD_REQUEST, "I-001", "인박스 레코드를 찾을 수 없습니다."),

    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

    BookLoanErrorCode(HttpStatus status, String code, String message) {
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
