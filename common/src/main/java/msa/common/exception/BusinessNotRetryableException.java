package msa.common.exception;


public class BusinessNotRetryableException extends RuntimeException {

    public BusinessNotRetryableException() {
    }

    public BusinessNotRetryableException(String message) {
        super(message);
    }

    public BusinessNotRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
