//package msa.bookloan.adapter.in.messaging.inbox.scheduler;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
//import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
//import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
//import msa.bookloan.application.event.BookCatalogChangedEvent;
//import msa.bookloan.application.projection.BookCatalogProjectionProcessor;
//import msa.bookloan.application.service.exception.InboxEventRecordNotFoundException;
//import msa.common.events.bookcatalog.BookCatalogChangedPayload;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class InboxRetryRecordProcessor {
//
//    private final BookCatalogProjectionProcessor projectionProcessor;
//    private final InboxEventRecordRepository eventRecordRepository;
//    private final ObjectMapper objectMapper;
//    private final InboxAppender inboxAppender;
//
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void retrySingleRecord(Long eventId) {
//        try {
//            projectionProcessor.retry(getEvent(eventId));
//            inboxAppender.recordSuccess(eventId);
//        } catch (Exception e) {
//            inboxAppender.recordFailure(eventId, e.getMessage());
//        }
//    }
//
//    private BookCatalogChangedEvent getEvent(Long eventId) {
//        InboxEventRecord eventRecord = eventRecordRepository.findByEventId(eventId)
//                .orElseThrow(() -> new InboxEventRecordNotFoundException(eventId));
//        return createBookCatalogUpdatedEvent(eventRecord);
//    }
//
//    private BookCatalogChangedEvent createBookCatalogUpdatedEvent(InboxEventRecord eventRecord) {
//        BookCatalogChangedPayload payload = getPayload(eventRecord);
//
//        return BookCatalogChangedEvent.builder()
//                .eventId(eventRecord.getEventId())
//                .eventType(eventRecord.getEventType())
//                .bookId(toLong(payload.bookId()))
//                .aggregateVersion(eventRecord.getAggregateVersion())
//                .title(payload.title())
//                .author(payload.author())
//                .category(payload.category())
//                .build();
//    }
//
//    private BookCatalogChangedPayload getPayload(InboxEventRecord eventRecord) {
//        try {
//            String payload = eventRecord.getPayload();
//            return objectMapper.readValue(payload, BookCatalogChangedPayload.class);
//        } catch (JsonProcessingException e) {
//            throw new IllegalStateException("Invalid JSON in inbox record: " + eventRecord.getId(), e);
//        }
//    }
//
//
//    private static long toLong(String s) {
//        return Long.parseLong(s.trim());
//    }
//}
