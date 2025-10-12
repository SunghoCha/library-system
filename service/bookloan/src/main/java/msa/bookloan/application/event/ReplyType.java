package msa.bookloan.application.event;

public enum ReplyType {
    MemberChecked,
    PointCharged,
    PointChargeFailed,
    InventoryReserved,
    InventoryReserveFailed,
    ShippingScheduled,
    ShippingScheduleFailed;

    public static ReplyType from(String s) {
        if (s == null) throw new IllegalArgumentException("replyType is null");
        switch (s) {
            case "MemberChecked":           return MemberChecked;
            case "PointCharged":            return PointCharged;
            case "PointChargeFailed":       return PointChargeFailed;
            case "InventoryReserved":       return InventoryReserved;
            case "InventoryReserveFailed":  return InventoryReserveFailed;
            case "ShippingScheduled":       return ShippingScheduled;
            case "ShippingScheduleFailed":  return ShippingScheduleFailed;
            default:
                throw new IllegalArgumentException("Unknown replyType=" + s);
        }
    }
}
