package msa.common.events.bookloan.saga.reply;

import msa.common.events.EventType;

public enum SagaReplyType implements EventType {

    MEMBER_CHECKED("member.checked"),
    INVENTORY_RESERVED("inventory.reserved"),
    INVENTORY_RESERVE_FAILED("inventory.reserve_failed"),
    POINT_CHARGED("point.charged"),
    POINT_CHARGE_FAILED("point.charge_failed"),
    SHIPPING_ACCEPTED("shipping.accepted"),
    SHIPPING_SCHEDULED("shipping.scheduled"),
    SHIPPING_SCHEDULE_FAILED("shipping.schedule_failed"),
    POINT_REFUNDED("point.refunded"),
    INVENTORY_RELEASED("inventory.released"),
    SHIPPING_CANCELLED("shipping.cancelled");

    private final String value;

    SagaReplyType(String value) {
        this.value = value;
    }

    @Override public String getValue() { return value; }
}
