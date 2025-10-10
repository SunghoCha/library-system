package msa.bookloan.application.event;

public enum ReplyType {
    MemberChecked,
    InventoryReserved,
    InventoryReserveFailed;

    public static ReplyType from(String s) {
        if (s == null) throw new IllegalArgumentException("replyType is null");
        switch (s) {
            case "MemberChecked": return MemberChecked;
            case "InventoryReserved": return InventoryReserved;
            case "InventoryReserveFailed": return InventoryReserveFailed;
            default: throw new IllegalArgumentException("Unknown replyType=" + s);
        }
    }
}
