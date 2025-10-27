package msa.bookloan.application.saga.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import msa.common.events.EventType;

@Getter
@RequiredArgsConstructor
public enum SagaCommandType implements EventType {

    // member
    MEMBER_CHECK("member.check"),

    // point
    POINT_CHARGE("point.charge"),
    POINT_REFUND("point.refund"),

    // inventory
    INVENTORY_RESERVE("inventory.reserve"),
    INVENTORY_RELEASE("inventory.release"),

    // shipping
    SHIPPING_SCHEDULE("shipping.schedule"),
    SHIPPING_CONFIRM("shipping.confirm"),
    SHIPPING_CANCEL("shipping.cancel");

    private final String value;

}
