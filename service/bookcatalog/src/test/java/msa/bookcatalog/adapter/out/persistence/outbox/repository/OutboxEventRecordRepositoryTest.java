package msa.bookcatalog.adapter.out.persistence.outbox.repository;

import jakarta.persistence.EntityManager;
import msa.bookcatalog.adapter.out.messaging.outbox.scheduler.OutboxEventSender;
import msa.bookcatalog.adapter.out.messaging.outbox.scheduler.OutboxRelayScheduler;
import msa.bookcatalog.adapter.out.persistence.outbox.EventRecorder;
import msa.bookcatalog.adapter.out.persistence.outbox.OutboxClaimerService;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.application.event.CatalogEventType;
import msa.bookcatalog.infra.config.QueryDslConfig;
import msa.bookcatalog.testsupport.time.TestClocks;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.dto.OutboxRouting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static java.time.LocalDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;

@Import(QueryDslConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {
        "app.aladin.enabled=false",
        "app.scheduling.enabled=false",
})
@ActiveProfiles("test")
class OutboxEventRecordRepositoryTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        EventRecorder eventRecorder() { return Mockito.mock(EventRecorder.class); }

        @Bean
        @Primary
        OutboxEventSender outboxEventSender() { return Mockito.mock(OutboxEventSender.class); }

        @Bean
        @Primary
        OutboxRelayScheduler bookCatalogOutboxRelayScheduler() { return Mockito.mock(OutboxRelayScheduler.class); }

        @Bean
        @Primary
        OutboxClaimerService outboxClaimerService() { return Mockito.mock(OutboxClaimerService.class); }
    }

    private final Clock fixedClock = TestClocks.FIXED_CLOCK;

    @Autowired
    private OutboxEventRecordRepository outboxRepository;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("lockClaimableIds: 발행 가능한 모든 종류의 이벤트를 조건에 맞게 조회한다")
    void lockClaimableIds_shouldFindAllClaimableEvents() {
        // given
        LocalDateTime now = now(fixedClock);
        int maxRetry = 3;

        // NEW 상태
        OutboxEventRecord newEvent = createRecord(100L,1L, OutboxEventRecordStatus.NEW, 0, now.minusMinutes(10));
        // FAILED 상태이고 재시도 횟수가 남은 이벤트
        OutboxEventRecord failedEvent = createRecord(200L,2L, OutboxEventRecordStatus.FAILED, maxRetry - 1, now.minusMinutes(9));
        // PUBLISHING 상태이고 lease가 만료된 이벤트
        OutboxEventRecord leaseOverEvent = createPublishingRecord(300L,3L, "worker", "lease1", now.minusMinutes(1));

        // PUBLISHING 상태이고 lease가 NULL인 이벤트 (비정상 종료 복구)
        OutboxEventRecord nullLeaseEvent = createPublishingRecord(800L, 8L, "null-worker", "lease2", null); // lease_until IS NULL

        // 발행 제외 : FAILED 상태이지만 재시도 횟수 초과
        OutboxEventRecord maxRetryEvent = createRecord(500L,5L, OutboxEventRecordStatus.FAILED, maxRetry, now.minusMinutes(8));
        // 발행 제외 : PUBLISHING 상태이고 lease가 유효함
        OutboxEventRecord lockedEvent = createPublishingRecord(600L,6L, "active-worker", "lease3", now.plusMinutes(5));
        // 발행 제외 : 이미 성공한 이벤트
        OutboxEventRecord publishedEvent = createRecord(700L,7L, OutboxEventRecordStatus.PUBLISHED, 0, now.minusMinutes(7));

        outboxRepository.saveAll(List.of(newEvent, failedEvent, leaseOverEvent, nullLeaseEvent, maxRetryEvent, lockedEvent, publishedEvent));

        // when
        List<Long> claimableIds = outboxRepository.lockClaimableIds(10, maxRetry, now);

        // then
        assertThat(claimableIds).hasSize(4)
                .contains(newEvent.getId(), failedEvent.getId(), leaseOverEvent.getId())
                .doesNotContain(maxRetryEvent.getId(), lockedEvent.getId(), publishedEvent.getId());
    }

    @Test
    @DisplayName("markPublishing: 여러 ID의 상태를 PUBLISHING으로 업데이트한다")
    void markPublishing_shouldUpdateStatusToPublishing() {
        // given
        OutboxEventRecord newEvent = createRecord(100L,1L, OutboxEventRecordStatus.NEW, 0, now());
        OutboxEventRecord failedEvent = createRecord(200L,2L, OutboxEventRecordStatus.FAILED, 1, now());
        outboxRepository.saveAll(List.of(newEvent, failedEvent));

        em.flush();
        em.clear();

        String workerId = "test-worker";
        String leaseId = UUID.randomUUID().toString();
        LocalDateTime now = now(fixedClock);
        LocalDateTime leaseUntil = now.plusSeconds(60);
        List<Long> ids = List.of(newEvent.getId(), failedEvent.getId());

        // when
        long updatedCount = outboxRepository.markPublishing(ids, workerId, leaseId, now, leaseUntil);

        // then
        assertThat(updatedCount).isEqualTo(2);
        List<OutboxEventRecord> updatedRecords = outboxRepository.findAllById(ids);
        assertThat(updatedRecords).allMatch(r -> r.getOutboxEventRecordStatus() == OutboxEventRecordStatus.PUBLISHING);
        assertThat(updatedRecords).allMatch(r -> r.getWorkerId().equals(workerId));
        assertThat(updatedRecords).allMatch(r -> r.getLeaseUntil().isAfter(now));
    }

    @Test
    @DisplayName("markPublished: 특정 워커가 선점한 이벤트를 PUBLISHED로 업데이트한다")
    void markPublished_shouldUpdateStatusToPublished() {
        // given
        String workerId = "test-worker";
        LocalDateTime now = now(fixedClock);
        String leaseId = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plusSeconds(60);
        OutboxEventRecord publishingEvent = createPublishingRecord(100L,1L, workerId, leaseId, leaseUntil);
        outboxRepository.save(publishingEvent);

        em.flush();
        em.clear();

        // when
        long updatedCount = outboxRepository.markPublished(List.of(publishingEvent.getId()), leaseId, now);

        // then
        assertThat(updatedCount).isEqualTo(1);
        OutboxEventRecord updatedRecord = outboxRepository.findById(publishingEvent.getId()).orElseThrow();
        assertThat(updatedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.PUBLISHED);
        assertThat(updatedRecord.getWorkerId()).isNull();
        assertThat(updatedRecord.getLeaseUntil()).isNull();
    }

    @Test
    @DisplayName("markFailed: 특정 워커가 선점한 이벤트를 FAILED로 업데이트한다")
    void markFailed_shouldUpdateStatusToFailedAndIncrementRetryCount() {
        // given
        String workerId = "test-worker";
        LocalDateTime now = now(fixedClock);
        String leaseId = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plusSeconds(60);
        OutboxEventRecord publishingEvent = createPublishingRecord(100L,1L, workerId, leaseId, leaseUntil);

        outboxRepository.save(publishingEvent);
        int initialRetryCount = publishingEvent.getRetryCount();

        em.flush();
        em.clear();
        // when
        String errorMessage = "Kafka Connection Failed";
        long updatedCount = outboxRepository.markFailed(List.of(publishingEvent.getId()), leaseId, errorMessage, now(fixedClock));

        // then
        assertThat(updatedCount).isEqualTo(1);
        OutboxEventRecord updatedRecord = outboxRepository.findById(publishingEvent.getId()).orElseThrow();
        assertThat(updatedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.FAILED);
        assertThat(updatedRecord.getRetryCount()).isEqualTo(initialRetryCount + 1);
        assertThat(updatedRecord.getLastError()).isEqualTo(errorMessage);
        assertThat(updatedRecord.getWorkerId()).isNull();
        assertThat(updatedRecord.getLeaseUntil()).isNull();
    }

    @Test
    @DisplayName("markDeadFromFailed: FAILED 상태의 이벤트를 DEAD_LETTER로 업데이트한다")
    void markDeadFromFailed_shouldUpdateStatusToDeadLetter() {
        // given
        OutboxEventRecord failedEvent = createRecord(100L,1L, OutboxEventRecordStatus.FAILED, 3, now());
        outboxRepository.save(failedEvent);

        em.flush();
        em.clear();

        // when
        String reason = "Max retry exceeded";
        long updatedCount = outboxRepository.markDeadFromFailed(failedEvent.getEventId(), reason, now(fixedClock));

        // then
        assertThat(updatedCount).isEqualTo(1);
        OutboxEventRecord updatedRecord = outboxRepository.findByEventId(failedEvent.getEventId()).orElseThrow();
        assertThat(updatedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.DEAD_LETTER);
        assertThat(updatedRecord.getLastError()).isEqualTo(reason);
    }

    @Test
    @DisplayName("tryClaimFromNew: NEW 상태의 이벤트를 선점하여 PUBLISHING으로 변경한다")
    void tryClaimFromNew_shouldClaimNewEvent() {
        // given
        OutboxEventRecord newEvent = createRecord(100L, 1L, OutboxEventRecordStatus.NEW, 0, now());
        outboxRepository.save(newEvent);

        em.flush();
        em.clear();

        String workerId = "claim-worker";
        String leaseId = UUID.randomUUID().toString();
        LocalDateTime now = now(fixedClock);
        LocalDateTime leaseUntil = now.plusSeconds(60);

        // when
        long updatedCount = outboxRepository.tryClaimFromNew(newEvent.getEventId(), workerId, leaseId, now, leaseUntil);

        // then
        assertThat(updatedCount).isEqualTo(1);
        OutboxEventRecord claimedRecord = outboxRepository.findByEventId(newEvent.getEventId()).orElseThrow();
        assertThat(claimedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.PUBLISHING);
        assertThat(claimedRecord.getWorkerId()).isEqualTo(workerId);
    }

    @Test
    @DisplayName("tryClaimFromNew: NEW 상태가 아닌 이벤트는 선점할 수 없다")
    void tryClaimFromNew_shouldNotClaimNonNewEvent() {
        // given
        OutboxEventRecord failedEvent = createRecord(100L,1L, OutboxEventRecordStatus.FAILED, 1, now());
        outboxRepository.save(failedEvent);

        String workerId = "claim-worker";
        String leaseId = UUID.randomUUID().toString();
        LocalDateTime now = now(fixedClock);
        LocalDateTime leaseUntil = now.plusSeconds(60);

        em.flush();
        em.clear();

        // when
        long updatedCount = outboxRepository.tryClaimFromNew(failedEvent.getEventId(), workerId, leaseId, now(), leaseUntil);

        // then
        assertThat(updatedCount).isEqualTo(0);
        OutboxEventRecord notUpdatedRecord = outboxRepository.findByEventId(failedEvent.getEventId()).orElseThrow();
        assertThat(notUpdatedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.FAILED);
    }


    private OutboxEventRecord createRecord(Long id, Long eventId, OutboxEventRecordStatus status, int retryCount, LocalDateTime occurredAt) {
        return OutboxEventRecord.builder()
                .id(id)
                .eventId(eventId)
                .eventType(CatalogEventType.CREATED.getValue())
                .aggregateId(999L)
                .aggregateType("BOOK_CATALOG")
                .aggregateVersion(0L)
                .payload("{}")
                .outboxEventRecordStatus(status)
                .retryCount(retryCount)
                .occurredAt(occurredAt)
                .routing(OutboxRouting.builder().topic("test-topic").partitionKey("key").build())
                .build();
    }

    private OutboxEventRecord createPublishingRecord(Long id,
                                                     Long eventId,
                                                     String workerId,
                                                     String leaseId,
                                                     LocalDateTime leaseUntil) {
        // occurredAt은 leaseUntil보다 무조건 빠르도록 설정
        LocalDateTime occurredAt = (leaseUntil != null) ? leaseUntil.minusMinutes(1) : now(fixedClock).minusMinutes(1);
        OutboxEventRecord record = createRecord(id, eventId, OutboxEventRecordStatus.PUBLISHING, 0, occurredAt);
        record.setWorkerId(workerId);
        record.setLeaseUntil(leaseUntil);
        record.setLeaseId(leaseId);
        return record;
    }
}

