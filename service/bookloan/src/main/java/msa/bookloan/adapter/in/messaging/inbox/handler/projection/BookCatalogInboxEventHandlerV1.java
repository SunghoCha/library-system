//package msa.bookloan.adapter.in.messaging.inbox.handler.projection;
//
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
//import msa.bookloan.application.event.BookCatalogChangedEvent;
//import msa.bookloan.application.projection.BookCatalogProjectionProcessorV1;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.event.TransactionPhase;
//import org.springframework.transaction.event.TransactionalEventListener;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class BookCatalogInboxEventHandlerV1 {
//    private static final int MAX_ERR_LEN = 2000;
//
//    private final BookCatalogProjectionProcessorV1 bookCatalogProjectionProcessorV1;
//    private final InboxAppender inboxAppender;
//
//    // TODO : 폴링스케줄러와 이 클래스의 로직 경로가 2군데인 구조. 사이드이펙트가 생길 수 있는 가능성대비 이런 설계가 효율이 큰건지 모르겠음
//    // 삭제하고 폴링스케줄러로 통일할지 고민
//    @Async("inboxExecutor")
//    @Transactional
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void handleBookCatalogUpdated(BookCatalogChangedEvent event) {
//        Long eventId = event.getEventId();
//        log.debug("catalog update start: BookCatalogUpdatedEvent [eventId={}, bookId={}]", eventId, event.getBookId());
//        try {
//            bookCatalogProjectionProcessorV1.project(event);
//            inboxAppender.recordSuccess(eventId);
//
//            log.debug("catalog update success PROCESSED [eventId={}]", eventId);
//
//        } catch (Exception e) {
//            try {
//                inboxAppender.recordFailure(eventId, e.getMessage());
//                log.info("catalog update failed [eventId={}]: {}", eventId, e.getMessage(), e);
//            } catch (Exception fatal) {
//                log.warn("inbox recordFailure failed eventId={}", eventId, fatal);
//            }
//
//        }
//    }
//
//}
