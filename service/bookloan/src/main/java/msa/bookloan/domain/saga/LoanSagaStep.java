package msa.bookloan.domain.saga;

public enum  LoanSagaStep {
    INIT,
    MEMBER_CHECKING,
    POINT_CHARGING,
    INVENTORY_RESERVING,
    SHIPPING_SCHEDULING,
    FINISHED

    // 취소/보상 흐름은 추후 추가
    // CANCEL_REQUESTED,
    // PAYMENT_REFUNDING,
    // INVENTORY_RELEASING,
    // SHIPPING_CANCELING,
}
