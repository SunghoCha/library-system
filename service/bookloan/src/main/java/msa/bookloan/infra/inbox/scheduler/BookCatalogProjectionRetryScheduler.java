package msa.bookloan.infra.inbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.infra.inbox.repository.BookCatalogProjectionInboxEventRecord;
import msa.bookloan.infra.inbox.repository.BookCatalogProjectionEventRecordRepository;
import msa.bookloan.infra.inbox.recorder.EventRecorder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import static msa.common.events.inbox.dto.InboxEventRecordStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCatalogProjectionRetryScheduler {

    private static final int MAX_RETRY_COUNT = 3;
    private final BookCatalogProjectionEventRecordRepository recordRepository;
    private final BookCatalogProjectionRetryRecordProcessor retryRecordProcessor;

    @Scheduled(fixedDelayString = "${inbox.retry.delay:60000}")
    public void retryInboxMessages() {
        log.info("Inbox retry scheduler running...");
        List<BookCatalogProjectionInboxEventRecord> failedList =
                recordRepository.findByInboxEventRecordStatusInAndRetryCountLessThan(List.of(NEW, FAILED), MAX_RETRY_COUNT);
        log.info("Found {} records to retry", failedList.size());

        for (BookCatalogProjectionInboxEventRecord eventRecord : failedList) {
            log.debug("Retrying event record: {}", eventRecord.getEventId());
            retryRecordProcessor.retrySingleRecord(eventRecord.getEventId());
        }
    }
}



