package msa.member.web.exception;

import lombok.extern.slf4j.Slf4j;
import msa.common.exception.BusinessException;
import msa.common.exception.CommonErrorCode;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static msa.common.exception.CommonErrorCode.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        HttpStatus status = e.getErrorCode().getStatus();
        String code = e.getErrorCode().getCode();
        String message = e.getErrorCode().getMessage();

        log.info("[BusinessException] Code: {}, Status: {}, Message: {}, Details: {}",
                code, status, message, e.getDetails());

        ErrorResponse errorResponse = ErrorResponse.of(e);

        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        log.info("[ValidationException] {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(INVALID_INPUT.getCode(), e.getBindingResult());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.info("[handleMethodArgumentTypeMismatchException]", e);
        ErrorResponse errorResponse = ErrorResponse.of(INVALID_PARAMETER_TYPE.getCode(), INVALID_PARAMETER_TYPE.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.info("[HttpRequestMethodNotSupportedException]", e);
        ErrorResponse errorResponse = ErrorResponse.of(METHOD_NOT_ALLOWED.getCode(), METHOD_NOT_ALLOWED.getMessage());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    @ExceptionHandler(FileUploadException.class)
    protected ResponseEntity<ErrorResponse> handleFileUploadException(FileUploadException e) {
        log.info("[FileUploadException]", e);
        ErrorResponse errorResponse = ErrorResponse.of(FILE_UPLOAD_ERROR.getCode(), FILE_UPLOAD_ERROR.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.warn("[UnhandledException]", e);
        ErrorResponse errorResponse = ErrorResponse.of(INTERNAL_SERVER_ERROR.getCode(), INTERNAL_SERVER_ERROR.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

}
