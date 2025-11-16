package msa.inventory.application.service.exception;

import msa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InventoryErrorCode implements ErrorCode {

    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "INV-001", "재고 정보를 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INV-002", "재고 수량이 부족합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    InventoryErrorCode(HttpStatus status, String code, String message) {
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
