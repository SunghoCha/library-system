package msa.common.events.bookcatalog;

import java.time.LocalDateTime;

public record BookCatalogDeletedPayload(
        String bookId,
        LocalDateTime occurredAt
) {}
