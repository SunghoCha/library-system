package msa.bookloan.domain.saga;

public enum SagaAbortReason {
    USER_CANCEL,
    TIMEOUT,
    BLACKLISTED,

    INVENTORY_RESERVE_FAILED,
    POINT_CHARGE_FAILED,
    SHIPPING_SCHEDULE_FAILED,

    COMPENSATION,   // 보상으로 실패 확정
    UNKNOWN;

    public static SagaAbortReason safeValueOf(String s) {
        if (s == null || s.isBlank()) return USER_CANCEL;
        try {
            return SagaAbortReason.valueOf(s);
        } catch (IllegalArgumentException ignore) {
            return UNKNOWN;
        }
    }
}
