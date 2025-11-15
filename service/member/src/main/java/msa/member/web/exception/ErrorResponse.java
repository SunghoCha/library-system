package msa.member.web.exception;

import lombok.Getter;
import msa.common.exception.BusinessException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.Map;

@Getter
public class ErrorResponse {

    private final String errorCode;
    private final String errorMessage;
    private final Map<String, Object> details;

    private ErrorResponse(String errorCode, String errorMessage, Map<String, Object> details) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.details = (details == null || details.isEmpty()) ? null : details;
    }

    public static ErrorResponse of(BusinessException e) {
        return new ErrorResponse(e.getErrorCode().getCode(), e.getErrorCode().getMessage(), e.getDetails());
    }

    public static ErrorResponse of(String errorCode, String errorMessage) {
        return new ErrorResponse(errorCode, errorMessage, null);
    }

    public static ErrorResponse of(String errorCode, BindingResult bindingResult) {
        return ErrorResponse.of(errorCode, createMessage(bindingResult));
    }

    public static String createMessage(BindingResult bindingResult) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;

        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            if (!isFirst) {
                sb.append(", ");
            } else {
                isFirst = false;
            }
            sb.append("[");
            sb.append(fieldError.getField());
            sb.append("] ");
            sb.append(fieldError.getDefaultMessage());
        }

        return sb.toString();
    }


}
