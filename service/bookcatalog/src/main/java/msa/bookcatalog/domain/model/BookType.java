package msa.bookcatalog.domain.model;

public enum BookType {
    NEW_RELEASE("신간"),
    POPULAR("인기"),
    STANDARD("일반"),
    UNKNOWN("알수없음");

    private final String displayName;
    BookType(String displayName) { this.displayName = displayName; }
    public String code() { return name(); }
    public String displayName() { return displayName; }
}

