package msa.common.events.bookcatalog;

import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;

import java.time.LocalDateTime;

public record BookCatalogSnapshotPayload(
        String bookId,
        String title,
        String author,
        CategoryRef category,
        BookTypeRef bookType,
        LocalDateTime occurredAt
) {
}
