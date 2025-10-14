package msa.common.events.bookcatalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;

import java.time.LocalDateTime;

public record BookCatalogChangedPayload(
        @NotBlank
        @Pattern(regexp = "^[0-9]+$")
        String eventId,

        @NotBlank
        String eventType,                          // CREATED/UPDATED/DELETED 등 문자열

        @NotBlank
        @Pattern(regexp = "^[0-9]+$")
        String bookId,

        @NotNull
        @Pattern(regexp = "^[0-9]+$")
        Long aggregateVersion,

        @NotNull
        @Pattern(regexp = "^[0-9]+$")
        String aggregateId,

        @NotBlank
        String aggregateType,                      // "BookCatalog"

        @NotBlank String title,
        @NotBlank String author,

        @NotNull CategoryRef category,
        @NotNull BookTypeRef bookType,

        @NotNull LocalDateTime occurredAt

) {
}
