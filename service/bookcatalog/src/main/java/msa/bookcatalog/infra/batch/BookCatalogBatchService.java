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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCatalogBatchService {

    private final EventRecorder eventRecorder;
    private final BookCatalogRepository bookCatalogRepository;
    private final BookCatalogEventMapper bookCatalogEventMapper;

    @Transactional
    public void processBookCatalogChunk(List<BatchItem> bookCatalogChunk) {
        if (bookCatalogChunk.isEmpty()) {
            return;
        }
        List<BookCatalog> bookCatalogs = bookCatalogChunk.stream().map(BatchItem::bookCatalog).toList();
        bookCatalogRepository.bulkUpsert(bookCatalogs);

        List<String> isbn13List = bookCatalogs.stream()
                .map(BookCatalog::getIsbn13)
                .toList();

        Map<String, BookCatalog> updatedBooksMap  = bookCatalogRepository.findAllByIsbn13In(isbn13List)
                .stream().collect(Collectors.toMap(BookCatalog::getIsbn13, Function.identity()));

        List<BookCatalogChangedEvent> bookCatalogChangedEvents = bookCatalogChunk.stream()
                .map(item -> {
                    BookCatalog updatedBookCatalog = updatedBooksMap.get(item.bookCatalog().getIsbn13());
                    return bookCatalogEventMapper.toEventFrom(updatedBookCatalog, item.eventType());
                })
                .toList();

        eventRecorder.saveAll(bookCatalogChangedEvents);

        log.info("{}건의 도서 정보와 Outbox 메시지 청크 처리 완료.", bookCatalogChunk.size());
    }


}
