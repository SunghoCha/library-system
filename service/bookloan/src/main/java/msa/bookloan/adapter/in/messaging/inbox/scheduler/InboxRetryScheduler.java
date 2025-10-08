package msa.bookloan.adapter.in.messaging.inbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import static msa.common.events.inbox.dto.InboxEventRecordStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxRetryScheduler {

    private static final int MAX_RETRY_COUNT = 3;
    private final InboxEventRecordRepository recordRepository;
    private final InboxRetryRecordProcessor retryRecordProcessor;

    @Scheduled(fixedDelayString = "${inbox.retry.delay:60000}")
    public void retryInboxMessages() {
        log.info("Inbox retry scheduler running...");
        List<InboxEventRecord> failedList =
                recordRepository.findByInboxEventRecordStatusInAndRetryCountLessThan(List.of(NEW, FAILED), MAX_RETRY_COUNT);
        log.info("Found {} records to retry", failedList.size());

        for (InboxEventRecord eventRecord : failedList) {
            log.debug("Retrying event record: {}", eventRecord.getEventId());
            retryRecordProcessor.retrySingleRecord(eventRecord.getEventId());
        }
    }
}



