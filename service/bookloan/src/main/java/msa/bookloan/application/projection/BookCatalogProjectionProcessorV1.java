//package msa.bookloan.application.projection;
//
//import jakarta.persistence.OptimisticLockException;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import msa.bookloan.adapter.out.persistence.projection.repository.BookCatalogProjectionRepository;
//import msa.bookloan.adapter.out.persistence.projection.entity.BookCatalogProjection;
//import msa.bookloan.application.event.BookCatalogChangedEvent;
//import msa.bookloan.application.event.CatalogEventType;
//import org.springframework.orm.ObjectOptimisticLockingFailureException;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class BookCatalogProjectionProcessorV1 {
//
//    private final BookCatalogProjectionRepository projectionRepository;
//
//    @Transactional
//    public void project(BookCatalogChangedEvent event) {
//        if (event.getEventType().equals(CatalogEventType.DELETED.getValue())) {
//            handleDeletedEvent(event);
//            return;
//        }
//        upsert(event);
//    }
//
//    // TODO : 스케줄러가 알아서 핸들러를 통해서 재시도하도록 하는게 나아보임
//    @Transactional
//    public void retry(BookCatalogChangedEvent event) {
//        // 삭제 이벤트면 삭제, 아니면 upsert 재시도
//        if (event.getEventType().equals(CatalogEventType.DELETED.getValue())) {
//            handleDeletedEvent(event);
//        } else {
//            upsert(event);
//        }
//    }
//
//    // 너무 지저분한거같은데 이게 맞나
//    // 기존 트랜잭션에 합류
//    // 순서꼬임 방지하기 위해 버전 체크
//    private void upsert(BookCatalogChangedEvent event) {
//        BookCatalogProjection existing = projectionRepository.findByBookId(event.getBookId()).orElse(null);
//
//        if (existing == null) {
//            projectionRepository.save(BookCatalogProjection.from(event));
//            log.debug("프로젝션 생성 [eventId={}, bookId={}, version={}]",
//                    event.getEventId(), event.getBookId(), event.getAggregateVersion());
//            return;
//        }
//
//        try {
//            if (existing.applySnapshot(event)) { // 버전 상위인지 체크
//                projectionRepository.save(existing);
//                log.debug("프로젝션 갱신 [eventId={}, bookId={}, version={}]",
//                        event.getEventId(), event.getBookId(), event.getAggregateVersion());
//            } else {
//                log.debug("프로젝션 스킵 [eventId={}, bookId={}, version={}]",
//                        event.getEventId(), event.getBookId(), event.getAggregateVersion());
//            }
//        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
//            log.info("프로젝션 갱신 충돌 스킵 [eventId={}, bookId={}, version={}]",
//                    event.getEventId(), event.getBookId(), event.getAggregateVersion());
//        }
//    }
//
//    private void handleDeletedEvent(BookCatalogChangedEvent event) {
//        // 레포지토리에 메서드 없으면 deleteById(event.getBookId()) 사용
//        projectionRepository.deleteByBookId(event.getBookId());
//        log.debug("Projection deleted [eventId={}, bookId={}]", event.getEventId(), event.getBookId());
//    }
//
//
//
//
//}
