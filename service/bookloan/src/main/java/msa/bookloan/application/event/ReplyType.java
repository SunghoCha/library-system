package msa.bookloan.application.event;

public enum ReplyType {
    MemberChecked,
    InventoryReserved, InventoryReserveFailed,
    PointCharged, PointChargeFailed,
    ShippingScheduled, ShippingScheduleFailed,
    PointRefunded,            // ★ 추가
    InventoryReleased;

    public static ReplyType from(String s) {
        if (s == null) throw new IllegalArgumentException("replyType is null");
        switch (s) {
            case "MemberChecked":           return MemberChecked;
            case "InventoryReserved":       return InventoryReserved;
            case "InventoryReserveFailed":  return InventoryReserveFailed;
            case "PointCharged":            return PointCharged;
            case "PointChargeFailed":       return PointChargeFailed;
            case "ShippingScheduled":       return ShippingScheduled;
            case "ShippingScheduleFailed":  return ShippingScheduleFailed;
            case "PointRefunded":           return PointRefunded;
            case "InventoryReleased":       return InventoryReleased;

            default:
                throw new IllegalArgumentException("Unknown replyType=" + s);
        }
    }
}
