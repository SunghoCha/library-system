package msa.bookloan.infra.messaging.inbox.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.messaging.inbox.recorder.InboxAppender;
import msa.bookloan.infra.messaging.inbox.repository.BookCatalogProjectionEventRecordRepository;
import msa.bookloan.infra.messaging.inbox.entity.InboxEventRecord;
import msa.bookloan.application.projection.BookCatalogProjectionProcessor;
import msa.bookloan.application.service.exception.InboxEventRecordNotFoundException;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxRetryRecordProcessor {

    private final BookCatalogProjectionProcessor projectionProcessor;
    private final BookCatalogProjectionEventRecordRepository eventRecordRepository;
    private final ObjectMapper objectMapper;
    private final InboxAppender inboxAppender;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retrySingleRecord(Long eventId) {
        try {
            projectionProcessor.retry(getEvent(eventId));
            inboxAppender.recordSuccess(eventId);
        } catch (Exception e) {
            inboxAppender.recordFailure(eventId, e.getMessage());
        }
    }

    private BookCatalogChangedEvent getEvent(Long eventId) {
        InboxEventRecord eventRecord = eventRecordRepository.findByEventId(eventId)
                .orElseThrow(() -> new InboxEventRecordNotFoundException(eventId));
        return createBookCatalogUpdatedEvent(eventRecord);
    }

    private BookCatalogChangedEvent createBookCatalogUpdatedEvent(InboxEventRecord eventRecord) {
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

    private BookCatalogChangedExternalEventPayload getPayload(InboxEventRecord eventRecord) {
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
