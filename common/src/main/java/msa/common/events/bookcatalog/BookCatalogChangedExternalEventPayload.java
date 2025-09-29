package msa.common.events.bookcatalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.DomainEventPayload;
import msa.common.events.EventType;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class BookCatalogChangedExternalEventPayload implements DomainEventPayload {

    @NotBlank
    private final String eventId;

    @NotNull
    private final EventType eventType;

    @NotBlank
    private final String bookId;

    @NotBlank
    private final String aggregateVersion;

    @NotBlank
    private final String aggregateType;

    @NotBlank @Size(max = 255)
    private final String title;

    @NotBlank @Size(max = 200)
    private final String author;

    @Valid
    @NotNull
    private final CategoryRef category; // 중첩 객체엔 @Valid 달아야 내부 필드까지 검증됨

    @Valid
    @NotNull
    private final BookTypeRef bookType;

    @NotNull private final LocalDateTime occurredAt;

    @Builder
    public BookCatalogChangedExternalEventPayload(String eventId, EventType eventType,
                                                  String bookId, String aggregateVersion,
                                                  String aggregateType,
                                                  String title, String author,
                                                  CategoryRef category,
                                                  BookTypeRef bookType,
                                                  LocalDateTime occurredAt) {
        this.eventId = normalizeUInt(eventId);
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.bookId = normalizeUInt(bookId);
        this.aggregateVersion = normalizeUInt(aggregateVersion);
        this.aggregateType = aggregateType;
        this.title = Objects.requireNonNull(title, "title");
        this.author = Objects.requireNonNull(author, "author");
        this.category = Objects.requireNonNull(category, "category");
        this.bookType = Objects.requireNonNull(bookType, "bookType");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static BookCatalogChangedExternalEventPayload of(BookCatalogChangedEvent event) {
        return BookCatalogChangedExternalEventPayload.builder()
                .eventId(String.valueOf(event.getEventId()))
                .eventType(event.getEventType())
                .bookId(String.valueOf(event.getBookId()))
                .aggregateVersion(String.valueOf(event.getAggregateVersion()))
                .aggregateType(event.getAggregateType())
                .title(event.getTitle())
                .author(event.getAuthor())
                .category(event.getCategory())
                .bookType(event.getBookType())
                .occurredAt(event.getOccurredAt())
                .build();
    }

    public BookCatalogChangedEvent toEvent() {
        return BookCatalogChangedEvent.builder()
                .eventId(Long.parseLong(eventId))
                .eventType(eventType)
                .bookId(Long.parseLong(bookId))
                .aggregateVersion(Long.parseLong(aggregateVersion))
                .title(title)
                .author(author)
                .category(category)
                .bookType(bookType)
                .occurredAt(occurredAt)
                .build();
    }

    @Override
    public String getAggregateId() {
        return bookId;
    }

    @Override
    public String getAggregateType() {
        return aggregateType;
    }

    @Override
    public String getAggregateVersion() {
        return aggregateVersion;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }


    // 방어적 코드 가져옴. 오버엔지니어링인지 아닌지 모르겠음
    private static String normalizeUInt(String v) {
        String s = Objects.requireNonNull(v, "numeric string").trim();
        if (!s.matches("^[0-9]+$")) {
            throw new IllegalArgumentException("Not unsigned integer: " + v);
        }
        // 선행 0 제거(모두 0이면 "0")
        int i = 0, n = s.length();
        while (i < n - 1 && s.charAt(i) == '0') i++;
        String norm = s.substring(i);
        // Long 범위 체크(선택) — 필요 없으면 제거 가능
        if (norm.length() > 19 || (norm.length() == 19 && norm.compareTo(String.valueOf(Long.MAX_VALUE)) > 0)) {
            throw new IllegalArgumentException("Out of range for 64-bit: " + v);
        }
        return norm;
    }
}
