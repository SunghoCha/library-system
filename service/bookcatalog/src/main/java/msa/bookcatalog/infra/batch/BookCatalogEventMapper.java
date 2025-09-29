package msa.bookcatalog.infra.batch;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class BookCatalogEventMapper {

    private final Snowflake snowflake;

    public BookCatalogChangedEvent toEventFrom(BookCatalog bookCatalog, EventType eventType) {
        CategoryRef categoryRef = new CategoryRef(bookCatalog.getCategory().categoryId(), bookCatalog.getCategory().categoryName());
        BookTypeRef bookTypeRef = new BookTypeRef(bookCatalog.getBookType().name(), bookCatalog.getBookType().displayName());

        // DB 작업(Upsert) 이후 다시 조회된 최신 상태여야 Version 정보 가짐
        return BookCatalogChangedEvent.builder()
                .eventId(snowflake.nextId()) // eventId는 항상 이벤트 생성시에 할당
                .eventType(eventType)
                .bookId(bookCatalog.getId())
                .aggregateVersion(bookCatalog.getVersion())
                .title(bookCatalog.getTitle())
                .author(bookCatalog.getAuthor())
                .category(categoryRef)
                .bookType(bookTypeRef)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
