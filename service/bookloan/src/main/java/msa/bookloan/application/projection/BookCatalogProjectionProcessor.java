package msa.bookloan.application.projection;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.projection.BookCatalogProjectionRepository;
import msa.bookloan.adapter.out.persistence.projection.entity.BookCatalogProjection;
import msa.bookloan.application.event.BookCatalogChangedEvent;
import msa.bookloan.application.event.CatalogEventType;
import msa.common.events.bookcatalog.BookCatalogDeletedPayload;
import msa.common.events.bookcatalog.BookCatalogSnapshotPayload;
import msa.common.exception.BusinessNotRetryableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCatalogProjectionProcessor {

    private final BookCatalogProjectionRepository projectionRepository;

    // 변수가 더 늘어나게 되면 컨텍스트 객체 만들어서 전달 받아야할 듯
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCreated(Long eventId, BookCatalogSnapshotPayload payload, Long aggregateVersion) {
        upsert(eventId, payload, requireVersion(aggregateVersion, payload));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onUpdated(Long eventId, BookCatalogSnapshotPayload payload, Long aggregateVersion) {
        upsert(eventId, payload, requireVersion(aggregateVersion, payload));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onDeleted(Long eventId, BookCatalogDeletedPayload payload, Long aggregateVersion) {
        Long bookId = toLong(payload.bookId());
        BookCatalogProjection existing = projectionRepository.findByBookId(bookId).orElse(null);
        if (existing == null) return;
        if (aggregateVersion == null || aggregateVersion < existing.getAggregateVersion()) {
            log.debug("삭제 스킵(낮은 버전) [eventId={}, bookId={}, incomingVer={}, currentVer={}]",
                    eventId, bookId, aggregateVersion, existing.getAggregateVersion());
            return;
        }
        projectionRepository.deleteByBookId(bookId);
        log.debug("Projection deleted [eventId={}, bookId={}]", eventId, payload.bookId());
    }

    private void upsert(Long eventId, BookCatalogSnapshotPayload payload, Long aggregateVersion) {
        Long bookId = toLong(payload.bookId());
        BookCatalogProjection existing = projectionRepository.findByBookId(bookId).orElse(null);

        if (existing == null) {
            projectionRepository.save(BookCatalogProjection.fromSnapshot(bookId, payload, aggregateVersion));
            log.debug("프로젝션 생성 [eventId={}, bookId={}, version={}]",
                    eventId, payload.bookId(), aggregateVersion);
            return;
        }

        try {
            if (existing.applySnapshot(payload, aggregateVersion)) { // 버전 상위인지 체크
                projectionRepository.save(existing);
                log.debug("프로젝션 갱신 [eventId={}, bookId={}, version={}]",
                        eventId, payload.bookId(), aggregateVersion);
            } else {
                log.debug("프로젝션 스킵 [eventId={}, bookId={}, version={}]",
                        eventId, payload.bookId(), aggregateVersion);
            }
        } catch (OptimisticLockingFailureException ex) {
            log.info("프로젝션 갱신 충돌 스킵 [eventId={}, bookId={}, version={}]",
                    eventId, payload.bookId(), aggregateVersion);
        }
    }

    private Long toLong(String stringId) {
        try {
            return Long.parseLong(stringId);
        } catch (NumberFormatException ex) {
            throw new BusinessNotRetryableException("Invalid bookId: " + stringId, ex);
        }
    }

    private long requireVersion(Long aggregateVersion, BookCatalogSnapshotPayload payload) {
        if (aggregateVersion == null) {
            throw new BusinessNotRetryableException(
                    "Missing aggregateVersion for catalog projection: bookId=" + payload.bookId());
        }
        return aggregateVersion; // 0도 허용. 진짜 값일 수 있음
    }


}
