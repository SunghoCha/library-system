package msa.bookcatalog.service.dto;

import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.domain.model.BookCategory;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;

import java.time.LocalDate;

public record UpdateBookCommand(

        Long bookCatalogId,
        String isbn13,
        String title,
        String author,
        String publisher,
        String description,
        String coverImageUrl,
        LocalDate publishDate,

        Integer categoryId,         // 도서 분류 ID
        String categoryName
) {

    public BookCatalog toEntity() {
        return BookCatalog.builder()
                .isbn13(isbn13)
                .title(title)
                .author(author)
                .description(description)
                .coverImageUrl(coverImageUrl)
                .publishDate(publishDate)
                .category(BookCategory.fromId(categoryId))
                .build();
    }

//    public BookCatalogChangedEvent toEvent(Long eventId) {
//        return BookCatalogChangedEvent.builder()
//                .eventId(eventId)
//                .eventType(EventType.UPDATED)
//                .title(title)
//                .author(author)
//                .category(BookType.STANDARD)
//                .build();
//    }
}
