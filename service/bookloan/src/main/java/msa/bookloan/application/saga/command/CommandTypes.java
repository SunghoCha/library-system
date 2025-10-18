package msa.bookloan.application.saga.command;

public class CommandTypes {
    // member
    public static final String MEMBER_CHECK = "member.check";

    // point
    public static final String POINT_CHARGE = "point.charge";
    public static final String POINT_REFUND = "point.refund";

    // inventory
    public static final String INVENTORY_RESERVE = "inventory.reserve";
    public static final String INVENTORY_RELEASE = "inventory.release";

    // shipping
    public static final String SHIPPING_SCHEDULE = "shipping.schedule";
    public static final String SHIPPING_CONFIRM  = "shipping.confirm";
    public static final String SHIPPING_CANCEL   = "shipping.cancel";

    private CommandTypes() {}
}
