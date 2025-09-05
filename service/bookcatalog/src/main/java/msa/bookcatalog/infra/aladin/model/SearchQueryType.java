package msa.bookcatalog.infra.aladin.model;

public enum SearchQueryType {

    Keyword("Keyword"), // 제목 + 저자 (디폴트)
    Title("Title"),
    Author("Author"),
    Publisher("Publisher");

    private final String value;

    SearchQueryType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}

