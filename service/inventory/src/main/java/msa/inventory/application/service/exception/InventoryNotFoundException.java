package msa.inventory.application.service.exception;

import msa.common.exception.BusinessException;

import java.util.Map;

public class InventoryNotFoundException extends BusinessException {

    public InventoryNotFoundException(Long bookId) {
        super(InventoryErrorCode.INVENTORY_NOT_FOUND, Map.of("bookId", bookId));
    }
}
