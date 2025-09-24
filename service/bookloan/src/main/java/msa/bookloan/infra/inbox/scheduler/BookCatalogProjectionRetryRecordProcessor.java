package msa.bookloan.infra.inbox.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.recorder.EventRecorder;
import msa.bookloan.infra.inbox.repository.BookCatalogProjectionEventRecordRepository;
import msa.bookloan.infra.inbox.repository.BookCatalogProjectionInboxEventRecord;
import msa.bookloan.service.BookCatalogProjectionService;
import msa.bookloan.service.exception.InboxEventRecordNotFoundException;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCatalogProjectionRetryRecordProcessor {

    private final BookCatalogProjectionService projectionService;
    private final BookCatalogProjectionEventRecordRepository eventRecordRepository;
    private final ObjectMapper objectMapper;
    private final EventRecorder eventRecorder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retrySingleRecord(Long eventId) {
        try {
            projectionService.retry(getEvent(eventId));
            eventRecorder.recordSuccess(eventId);
        } catch (Exception e) {
            eventRecorder.recordFailure(eventId, e.getMessage());
        }
    }

    private BookCatalogChangedEvent getEvent(Long eventId) {
        BookCatalogProjectionInboxEventRecord eventRecord = eventRecordRepository.findByEventId(eventId)
                .orElseThrow(() -> new InboxEventRecordNotFoundException(eventId));
        return createBookCatalogUpdatedEvent(eventRecord);
    }

    private BookCatalogChangedEvent createBookCatalogUpdatedEvent(BookCatalogProjectionInboxEventRecord eventRecord) {
        BookCatalogChangedExternalEventPayload payload = getPayload(eventRecord);

        return BookCatalogChangedEvent.builder()
                .eventId(eventRecord.getEventId())
                .eventType(eventRecord.getEventType())
                .bookId(toLong(payload.getBookId()))
                .aggregateVersion(eventRecord.getAggregateVersion())
                .title(payload.getTitle())
                .author(payload.getAuthor())
                .category(payload.getCategory())
                .build();
    }

    private BookCatalogChangedExternalEventPayload getPayload(BookCatalogProjectionInboxEventRecord eventRecord) {
        try {
            String payload = eventRecord.getPayload();
            return objectMapper.readValue(payload, BookCatalogChangedExternalEventPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid JSON in inbox record: " + eventRecord.getId(), e);
        }
    }


    private static long toLong(String s) {
        return Long.parseLong(s.trim());
    }
}
