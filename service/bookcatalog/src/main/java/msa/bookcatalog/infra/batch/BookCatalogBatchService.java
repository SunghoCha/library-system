package msa.bookcatalog.infra.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.domain.model.BookType;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.bookcatalog.repository.BookCatalogRepository;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.snowflake.Snowflake;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookCatalogBatchService {

    private final Snowflake snowflake;
    private final EventRecorder eventRecorder;
    private final BookCatalogRepository bookCatalogRepository;
    private final BookCatalogOutboxEventRecordRepository outboxRepository; // Outbox 저장 로직이 있다면

    @Transactional
    public void processBookCatalogChunk(List<BookCatalog> bookCatalogChunk) {
        if (bookCatalogChunk.isEmpty()) {
            return;
        }

        // 1. 도메인 데이터 벌크 저장
        bookCatalogRepository.bulkUpsert(bookCatalogChunk);

        // 2. Outbox 데이터 벌크 저장 (하이브리드 모델의 '보장 경로')
        List<BookCatalogChangedEvent> outboxRecords = bookCatalogChunk.stream()
                .map(this::createUpsertEventFrom) // BookCatalog -> OutboxRecord
                .toList();
        eventRecorder.saveAll(outboxRecords);

        log.info("{}건의 도서 정보와 Outbox 메시지 청크 처리 완료.", bookCatalogChunk.size());
    }

    private BookCatalogChangedEvent  createUpsertEventFrom(BookCatalog bookCatalog) {
        CategoryRef categoryRef = new CategoryRef(
                bookCatalog.getCategory().categoryId(),
                bookCatalog.getCategory().categoryName()
        );
        BookTypeRef bookTypeRef = new BookTypeRef(
                bookCatalog.getBookType().code(),
                bookCatalog.getBookType().displayName()
        );
        long version = (bookCatalog.get != null) ? bookCatalog.getVersion() : 0L;

        return null;
    }

    private BookCatalogChangedEvent createFrom(BookCatalog book) {

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
