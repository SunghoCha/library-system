package msa.bookloan.application.event;

import msa.common.events.EventType;

public enum CatalogEventType implements EventType {

    CREATED("bookcatalog.created"),
    UPDATED("bookcatalog.updated"),
    DELETED("bookcatalog.deleted");

    private final String value;

    CatalogEventType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
