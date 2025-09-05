package msa.bookcatalog.infra.aladin.model;

public enum ListQueryType {
    Bestseller("Bestseller"),
    ItemNewAll("ItemNewAll"),
    ItemEditorChoice("ItemEditorChoice"),
    ItemNewSpecial("ItemNewSpecial"),
    BlogBest("BlogBest");

    private final String value;

    ListQueryType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
