package msa.common.domain.model;

public enum BookCategory {
    NEW_RELEASE("신간"),
    POPULAR("인기"),
    STANDARD("일반");

    private String name;

    BookCategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
