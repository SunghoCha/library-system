package msa.bookloan.domain.exception.errorCode;

import msa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BookLoanErrorCode implements ErrorCode {

    OVERDUE_BLOCKED(HttpStatus.CONFLICT, "BL-001", "연체 중인 대출이 존재합니다."),
    LOAN_NOT_FOUND (HttpStatus.NOT_FOUND,  "BL-002", "요청하신 대출을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    BookLoanErrorCode(HttpStatus status, String code, String message) {
        this.status = status; this.code = code; this.message = message;
    }
    @Override public HttpStatus getStatus() { return status; }
    @Override public String getCode()   { return code; }
    @Override public String getMessage(){ return message; }
}
