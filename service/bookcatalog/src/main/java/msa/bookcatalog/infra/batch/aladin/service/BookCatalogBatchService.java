package msa.bookcatalog.infra.batch.aladin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.infra.batch.aladin.BookCatalogEventMapper;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.bookcatalog.repository.BookCatalogRepository;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    public void processBookCatalogChunk(List<BookCatalog> bookCatalogs) {
        if (bookCatalogs.isEmpty()) {
            return;
        }
        List<String> isbns = bookCatalogs.stream().map(BookCatalog::getIsbn13).toList();
        Map<String, BookCatalog> existingBookMap = bookCatalogRepository.findAllByIsbn13In(isbns).stream()
                .collect(Collectors.toMap(BookCatalog::getIsbn13, Function.identity()));

        List<BookCatalog> toInsert = new ArrayList<>();
        List<BookCatalog> toUpdate = new ArrayList<>();

        for (BookCatalog incomingBook : bookCatalogs) {
            BookCatalog existingBook = existingBookMap.get(incomingBook.getIsbn13());

            if (existingBook == null) {
                toInsert.add(incomingBook);
            } else {
                BookCatalog.BookCatalogEditor editor = buildPatchEditor(existingBook, incomingBook);
                if (existingBook.applyEditor(editor)) {
                    toUpdate.add(existingBook);
                }
            }
        }

        if (!toInsert.isEmpty()) {
            bookCatalogRepository.saveAll(toInsert);
        }

        bookCatalogRepository.flush(); // version 정보 업데이트

        ArrayList<BookCatalogChangedEvent> events = new ArrayList<>(toInsert.size() + toUpdate.size());
        toInsert.forEach(book -> events.add(bookCatalogEventMapper.toEventFrom(book, EventType.CREATED)));
        toUpdate.forEach(book -> events.add(bookCatalogEventMapper.toEventFrom(book, EventType.UPDATED)));

        if (!events.isEmpty()) {
            eventRecorder.saveAll(events);
        }

        log.info("신규 {}건, 변경 {}건 처리(이벤트 {}건).", toInsert.size(), toUpdate.size(), events.size());
    }

    private static BookCatalog.BookCatalogEditor buildPatchEditor(BookCatalog existingBook, BookCatalog incomingBook) {
        return existingBook.toEditorBuilder()
                .title(incomingBook.getTitle())
                .author(incomingBook.getAuthor())
                .publisher(incomingBook.getPublisher())
                .coverImageUrl(incomingBook.getCoverImageUrl())
                .description(incomingBook.getDescription())
                .bookType(incomingBook.getBookType())
                .publishDate(incomingBook.getPublishDate())
                .category(incomingBook.getCategory())
                .build();
    }

}
