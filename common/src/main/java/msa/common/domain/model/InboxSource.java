package msa.common.domain.model;

public enum InboxSource {
    BOOK_CATALOG("book-catalog"),
    INVENTORY("inventory"),
    PAYMENT("payment"),
    MEMBER("member");
    //... 추가될 다른 서비스들

    private final String value;

    InboxSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static InboxSource fromValue(String value) {
        for (InboxSource s : InboxSource.values()) {
            if (s.value.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown InboxSource: " + value);
    }
}
