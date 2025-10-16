package msa.bookloan.domain.saga;

public enum  LoanSagaStep {
    INIT,
    MEMBER_CHECKING,
    INVENTORY_RESERVING,
    POINT_CHARGING,

    SHIPPING_SCHEDULING,   // 배송 예약 커맨드 보냄
    SHIPPING_ACCEPTED,     // 배송 서비스가 예약/접수됨 리플라이 발행 - 취소 가능 구간
    SHIPPING_CONFIRMING,

    FINISHED

    // 취소/보상 흐름은 추후 추가
    // CANCEL_REQUESTED,
    // PAYMENT_REFUNDING,
    // INVENTORY_RELEASING,
    // SHIPPING_CANCELING,
}
