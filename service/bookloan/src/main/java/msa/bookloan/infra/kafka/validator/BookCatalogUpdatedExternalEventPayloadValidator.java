package msa.bookloan.infra.kafka.validator;

import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;

import java.time.LocalDateTime;

public class BookCatalogUpdatedExternalEventPayloadValidator {

    // TODO : 필드명 별로 다 분리해서 검증하고 에러메시지 저장하도록 해야하는지, 오버엔지니어링인지 고민
    public static boolean isValid(BookCatalogChangedExternalEventPayload payload) {

        return  payload != null &&
                isPositiveLong(payload.getEventId()) &&
                payload.getEventType() != null &&
                isPositiveLong(payload.getAggregateId()) &&
                isPositiveLong(payload.getAggregateVersion()) &&
                trimToNull(payload.getTitle()) != null &&
                trimToNull(payload.getAuthor()) != null &&
                payload.getCategory() != null &&
                payload.getOccurredAt() != null;
    }


    private static String trimToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isPositiveLong(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.isEmpty()) return false;
        try {
            return Long.parseLong(s) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
