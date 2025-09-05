package msa.bookloan.infra.kafka.validator;

import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;

public class BookCatalogUpdatedExternalEventPayloadValidator {

    public static boolean isValid(BookCatalogChangedExternalEventPayload payload) {
        if (payload == null) return false;

        return isNotBlank(payload.getEventId()) &&
                payload.getEventType() != null &&
                isNotBlank(payload.getBookId()) &&
                isNotBlank(payload.getTitle()) &&
                isNotBlank(payload.getAuthor()) &&
                payload.getCategory() != null &&
                payload.getOccurredAt() != null;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
