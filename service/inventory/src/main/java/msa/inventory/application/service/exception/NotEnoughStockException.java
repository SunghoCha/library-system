package msa.inventory.application.service.exception;

import msa.common.exception.BusinessException;

import java.util.Map;

public class NotEnoughStockException extends BusinessException {

    public NotEnoughStockException(Long bookId) {
        super(InventoryErrorCode.INSUFFICIENT_STOCK, Map.of("bookId", bookId));
    }
}
