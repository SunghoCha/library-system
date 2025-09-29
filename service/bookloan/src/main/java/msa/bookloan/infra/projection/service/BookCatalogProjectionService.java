package msa.bookloan.infra.projection.service;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.recorder.EventRecorder;
import msa.bookloan.infra.projection.BookCatalogProjection;
import msa.bookloan.infra.projection.repository.BookCatalogProjectionRepository;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
        if (event.getEventType() == EventType.DELETED) {
            handleDeletedEvent(event);
            return;
        }
        upsert(event);
    }

    @Transactional
    public void retry(BookCatalogChangedEvent event) {
        // 삭제 이벤트면 삭제, 아니면 upsert 재시도
        if (event.getEventType() == EventType.DELETED) {
            handleDeletedEvent(event);
        } else {
            upsert(event);
        }
    }

    private void upsert(BookCatalogChangedEvent e) {
        var existing = projectionRepository.findByBookId(e.getBookId()).orElse(null);

        if (existing == null) {
            projectionRepository.save(BookCatalogProjection.from(e));
            log.debug("프로젝션 생성 [eventId={}, bookId={}, version={}]",
                    e.getEventId(), e.getBookId(), e.getAggregateVersion());
            return;
        }

        try {
            if (existing.applySnapshot(e)) {
                projectionRepository.save(existing);
                log.debug("프로젝션 갱신 [eventId={}, bookId={}, version={}]",
                        e.getEventId(), e.getBookId(), e.getAggregateVersion());
            } else {
                log.debug("프로젝션 스킵 [eventId={}, bookId={}, version={}]",
                        e.getEventId(), e.getBookId(), e.getAggregateVersion());
            }
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
            log.info("프로젝션 갱신 충돌 스킵 [eventId={}, bookId={}, version={}]",
                    e.getEventId(), e.getBookId(), e.getAggregateVersion());
        }
    }

    private void handleDeletedEvent(BookCatalogChangedEvent event) {
        // 레포지토리에 메서드 없으면 deleteById(event.getBookId()) 사용
        projectionRepository.deleteByBookId(event.getBookId());
        log.debug("Projection deleted [eventId={}, bookId={}]", event.getEventId(), event.getBookId());
    }




}
