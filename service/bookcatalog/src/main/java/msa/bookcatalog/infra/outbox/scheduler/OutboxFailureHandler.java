package msa.bookcatalog.infra.outbox.scheduler;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxFailureHandler {

    private final BookCatalogOutboxEventRecordRepository eventRecordRepository;

    private static final int MAX_RETRY_COUNT = 3;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(Long eventRecordId, Exception e) {
        log.warn("Handling failure for event record id: {}", eventRecordId, e);

        // 반드시 ID로 다시 조회해서 현재 트랜잭션의 영속성 컨텍스트에 포함시켜야 합니다.
        BookCatalogOutboxEventRecord eventRecord = eventRecordRepository.findById(eventRecordId)
                .orElse(null);

        if (eventRecord == null) {
            log.error("Outbox event record not found with id: {}", eventRecordId);
            return;
        }

        eventRecord.incrementRetryCount();

        if (isRetryLimitExceeded(eventRecord)) {
            log.info("Retry limit exceeded for eventId {}. Marking as Dead Letter.", eventRecord.getEventId());
            eventRecord.markAsDeadLetter(e.getMessage()); // 엔티티에 직접 상태 변경 메서드 추가 (아래 참고)
        }
        // 이 메서드가 종료되면, 변경된 재시도 횟수나 DEAD_LETTER 상태가 커밋됩니다.
    }

    private boolean isRetryLimitExceeded(BookCatalogOutboxEventRecord eventRecord) {
        return eventRecord.getRetryCount() >= MAX_RETRY_COUNT;
    }
}
