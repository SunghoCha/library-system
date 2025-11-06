package msa.bookloan.domain.exception.errorCode;

import msa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MemberErrorCode implements ErrorCode {
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MB-001", "회원을 찾을 수 없습니다."),
    MEMBER_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MB-002", "멤버 서비스가 응답하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    MemberErrorCode(HttpStatus status, String code, String message) {
        this.status = status; this.code = code; this.message = message;
    }
    @Override public HttpStatus getStatus() { return status; }
    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}
