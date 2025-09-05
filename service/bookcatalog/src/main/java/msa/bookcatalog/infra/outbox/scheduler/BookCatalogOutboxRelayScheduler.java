package msa.bookcatalog.infra.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.OutboxEventSender;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecord;
import msa.bookcatalog.infra.outbox.repository.BookCatalogOutboxEventRecordRepository;
import msa.common.events.outbox.OutboxEventRecordStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static msa.common.events.outbox.OutboxEventRecordStatus.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookCatalogOutboxRelayScheduler {

    private static final int MAX_RETRY_COUNT = 3;
    private final BookCatalogOutboxEventRecordRepository eventRecordRepository;
    private final OutboxEventSender outboxEventSender;
    private final EventRecorder eventRecorder;

    @Value("${kafka.topics.book-catalog-changed}")
    private String topic;

    @Scheduled(fixedDelayString = "${outbox.relay.delay:60000}", initialDelayString = "${outbox.relay.initialDelay:10000}")
    public void retryOutboxMessages() {
        List<BookCatalogOutboxEventRecord> failedList =
                eventRecordRepository.findByOutboxEventRecordStatusInAndRetryCountLessThan(List.of(NEW, FAILED), MAX_RETRY_COUNT);

        for (BookCatalogOutboxEventRecord eventRecord : failedList) {
            try {
                outboxEventSender.resendEvent(eventRecord, topic);
            } catch (Exception e) {
                eventRecord.incrementRetryCount();
                if (isRetryLimitExceeded(eventRecord)) {
                    log.info("Retry limit exceeded for eventId {}. Delegating to markAsDeadLetter", eventRecord.getEventId());
                    eventRecorder.markAsDeadLetter(eventRecord.getEventId());
                }
            }
        }
    }

    private static boolean isRetryLimitExceeded(BookCatalogOutboxEventRecord eventRecord) {
        return eventRecord.getRetryCount() >= MAX_RETRY_COUNT;
    }
}
