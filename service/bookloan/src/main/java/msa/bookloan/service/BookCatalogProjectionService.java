package msa.bookloan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.recorder.EventRecorder;
import msa.bookloan.infra.projection.BookCatalogProjection;
import msa.bookloan.infra.projection.BookCatalogProjectionEditor;
import msa.bookloan.infra.projection.BookCatalogProjectionRepository;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCatalogProjectionService {

    private final BookCatalogProjectionRepository projectionRepository;
    private final EventRecorder eventRecorder;

    @Transactional
    public void project(BookCatalogChangedEvent event) {
            switch (event.getEventType()) {
                case CREATED -> handleCreatedEvent(event);
                case UPDATED -> handleUpdatedEvent(event);
                case DELETED -> handleDeletedEvent(event);
            }
    }

    @Transactional
    public void retry(BookCatalogChangedEvent event) {
        projectionRepository.save(createBookCatalogProjection(event));
        log.debug("Retry projection success [eventId={}, bookId={}]", event.getEventId(), event.getBookId());
    }

    private void handleDeletedEvent(BookCatalogChangedEvent event) {
        projectionRepository.deleteByBookId(event.getBookId());
    }

    private void handleUpdatedEvent(BookCatalogChangedEvent event) {
        projectionRepository.findByBookId(event.getBookId())
                .ifPresentOrElse(
                        existingBookProjection -> {
                            existingBookProjection.edit(createEditor(event, existingBookProjection));
                        },
                        () -> {
                            log.info("Projection missing for UPDATED event. Treating as CREATED [eventId={}, bookId={}]",
                                    event.getEventId(), event.getBookId());
                            projectionRepository.save(createBookCatalogProjection(event));
                        }
                );
    }

    private static BookCatalogProjectionEditor createEditor(BookCatalogChangedEvent event, BookCatalogProjection existingBookProjection) {
        return existingBookProjection.toEditorBuilder()
                .title(event.getTitle())
                .author(event.getAuthor())
                .bookCategory(event.getCategory())
                .build();
    }

    private void handleCreatedEvent(BookCatalogChangedEvent event) {
        projectionRepository.save(createBookCatalogProjection(event));
    }

    private BookCatalogProjection createBookCatalogProjection(BookCatalogChangedEvent event) {
        return BookCatalogProjection.builder()
                .bookId(event.getBookId())
                .title(event.getTitle())
                .author(event.getAuthor())
                .bookCategory(event.getCategory())
                .build();
    }



}
