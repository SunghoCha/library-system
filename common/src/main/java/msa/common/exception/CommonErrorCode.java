package msa.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode {

    // 400 Bad Request
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "CM-001", "Invalid input value"),
    INVALID_PARAMETER_TYPE(HttpStatus.BAD_REQUEST, "CM-002", "Invalid parameter type"),
    FILE_UPLOAD_ERROR(HttpStatus.BAD_REQUEST, "CM-003", "File upload error"),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "CM-004", "Method not allowed"),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CM-005", "Internal server error");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;


}
