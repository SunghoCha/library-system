package msa.bookloan.application.saga.exception.errorCode;

import msa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SagaErrorCode implements ErrorCode {

    SAGA_NOT_FOUND(HttpStatus.NOT_FOUND, "S-001", "요청하신 사가를 찾을 수 없습니다."),
    SAGA_STATE_MISMATCH(HttpStatus.CONFLICT, "S-002", "사가 상태/스텝이 일치하지 않습니다."),
    SAGA_ALREADY_TERMINAL(HttpStatus.CONFLICT, "S-003", "이미 터미널 상태의 사가입니다."),
    SAGA_PIVOT_PASSED(HttpStatus.CONFLICT, "S-004", "피벗 이후 단계로 취소/보상이 불가합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    SagaErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus getStatus() { return status; }
    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}
