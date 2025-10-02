package msa.bookcatalog.infra.outbox.repository;

import jakarta.persistence.EntityManager;
import msa.bookcatalog.config.QueryDslConfig;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.common.events.EventType;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.dto.OutboxRouting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QueryDslConfig.class)
@DataJpaTest(properties = {
        "app.aladin.enabled=false",
        "app.scheduling.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookCatalogOutboxEventRecordRepositoryImplTest {

    @Autowired
    private BookCatalogOutboxEventRecordRepository outboxRepository;

    @Test
    @DisplayName("findEventsToRetryWithSkipLock: 복잡한 조건에 맞는 재시도 대상 이벤트들을 정확히 조회한다")
    void findEventsToRetry_조건에_맞는_이벤트만_반환한다() {
        // given: 다양한 상태의 테스트 데이터들을 실제 MySQL DB에 미리 저장
        outboxRepository.save(createRecord(1L, OutboxEventRecordStatus.NEW, 0, LocalDateTime.now().minusMinutes(5)));
        outboxRepository.save(createRecord(2L, OutboxEventRecordStatus.FAILED, 2, LocalDateTime.now().minusDays(1)));
        BookCatalogOutboxEventRecord publishingAndStale = createRecord(3L, OutboxEventRecordStatus.PUBLISHING, 1, LocalDateTime.now().minusDays(1));
        publishingAndStale.setPickedAt(LocalDateTime.now().minusMinutes(10));
        outboxRepository.save(publishingAndStale);

        // --- 선택되면 안 되는 데이터 ---
        outboxRepository.save(createRecord(4L, OutboxEventRecordStatus.NEW, 0, LocalDateTime.now()));
        outboxRepository.save(createRecord(5L, OutboxEventRecordStatus.FAILED, 5, LocalDateTime.now().minusDays(1)));
        BookCatalogOutboxEventRecord publishingAndFresh = createRecord(6L, OutboxEventRecordStatus.PUBLISHING, 1, LocalDateTime.now().minusDays(1));
        publishingAndFresh.setPickedAt(LocalDateTime.now());
        outboxRepository.save(publishingAndFresh);
        outboxRepository.save(createRecord(7L, OutboxEventRecordStatus.PUBLISHED, 1, LocalDateTime.now().minusDays(1)));

        // when
        List<BookCatalogOutboxEventRecord> results = outboxRepository.findEventsToRetryWithSkipLock(
                5, 10,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().minusMinutes(1)
        );

        // then
        assertThat(results).hasSize(3);
        assertThat(results)
                .extracting(BookCatalogOutboxEventRecord::getEventId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    private BookCatalogOutboxEventRecord createRecord(Long eventId, OutboxEventRecordStatus status, int retryCount, LocalDateTime occurredAt) {
        return BookCatalogOutboxEventRecord.builder()
                .id(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE) // 랜덤 ID
                .eventId(eventId)
                .eventType(EventType.CREATED)
                .aggregateId("agg-id-" + eventId)
                .aggregateType("BOOK_CATALOG")
                .aggregateVersion(0L)
                .payload("{}")
                .outboxEventRecordStatus(status)
                .retryCount(retryCount)
                .occurredAt(occurredAt)
                .routing(OutboxRouting.builder().topic("test-topic").partitionKey("key").build())
                .build();
    }
}

