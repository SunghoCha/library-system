package msa.bookloan.adapter.out.messaging.inbox.scheduler;

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

    // TODO : 범용성 떨어지고 분산 환경 고려안된 구버전 스케줄러라서 아예 다시 만들어야할지도
    private static final int MAX_RETRY_COUNT = 3; // TODO : 외부 변수화해야함
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



