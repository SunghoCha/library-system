package msa.bookcatalog.infra.batch;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class BookCatalogEventMapper {

    private final Snowflake snowflake;

    public BookCatalogChangedEvent toChangedEvent(BookCatalog book, Long eventId) {
        CategoryRef categoryRef = new CategoryRef(book.getCategory().categoryId(), book.getCategory().categoryName());
        BookTypeRef bookTypeRef = new BookTypeRef(book.getBookType().name(), book.getBookType().displayName());

        return BookCatalogChangedEvent.builder()
                .eventId(snowflake.nextId()) // 새로운 이벤트 ID 생성
                .eventType(EventType.BOOK_CATALOG_UPSERTED) // 배치 작업의 의미에 맞는 이벤트 타입
                .bookId(book.getId()) // 엔티티의 ID를 이벤트의 bookId로 사용
                .aggregateVersion(0L) // TODO: 엔티티에 @Version 필드가 있다면 그 값을 사용해야 함
                .aggregateType("BookCatalog") // 애그리거트 타입 명시
                .title(book.getTitle())
                .author(book.getAuthor())
                .category(category)
                .bookType(new BookTypeRef(book.getBookType().code(), book.getBookType().displayName()))
                .occurredAt(LocalDateTime.now()) // 이벤트 발생 시각
                .build();
    }
}
