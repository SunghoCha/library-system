package msa.bookcatalog.infra.outbox.repository;

import msa.bookcatalog.config.QueryDslConfig;
import msa.bookcatalog.infra.outbox.OutboxEventSender;
import msa.bookcatalog.infra.outbox.scheduler.BookCatalogOutboxRelayScheduler;
import msa.bookcatalog.infra.outbox.service.OutboxClaimerService;
import msa.common.events.EventType;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.dto.OutboxRouting;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QueryDslConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {
        "app.aladin.enabled=false",
        "app.scheduling.enabled=false"
})
class BookCatalogOutboxEventRecordRepositoryTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        OutboxEventSender outboxEventSender() { return Mockito.mock(OutboxEventSender.class); }

        @Bean
        @Primary
        BookCatalogOutboxRelayScheduler bookCatalogOutboxRelayScheduler() { return Mockito.mock(BookCatalogOutboxRelayScheduler.class); }

        @Bean
        @Primary
        OutboxClaimerService outboxClaimerService() { return Mockito.mock(OutboxClaimerService.class); }
    }

    @Autowired
    private BookCatalogOutboxEventRecordRepository outboxRepository;

    @Test
    @DisplayName("failAndIncrementIfCurrent: 주어진 조건에 맞으면 상태를 FAILED로 변경하고, 에러 메시지와 재시도 횟수를 갱신한다")
    void failAndIncrementIfCurrent_성공() {
        // given
        BookCatalogOutboxEventRecord saved = outboxRepository.save(createRecord(20L, OutboxEventRecordStatus.PUBLISHING, 5));
        String errorMessage = "Kafka Publish Failed";

        // when
        int updatedCount = outboxRepository.failAndIncrementIfCurrent(
                saved.getEventId(),
                OutboxEventRecordStatus.PUBLISHING,
                OutboxEventRecordStatus.FAILED,
                errorMessage
        );

        // then
        // 1개의 행이 업데이트되었는지 확인
        assertThat(updatedCount).isEqualTo(1);

        // DB에서 최신 상태를 다시 조회
        BookCatalogOutboxEventRecord updatedRecord = outboxRepository.findByEventId(saved.getEventId()).get();

        assertThat(updatedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.FAILED);
        assertThat(updatedRecord.getLastError()).isEqualTo(errorMessage);
        assertThat(updatedRecord.getRetryCount()).isEqualTo(6); // 5 -> 6
    }

    @Test
    @DisplayName("failAndIncrementIfCurrent: from 상태가 일치하지 않으면 아무것도 변경하지 않는다")
    void failAndIncrementIfCurrent_실패_상태불일치() {
        // given
        // 상태가 NEW인 레코드를 저장. from 조건인 PUBLISHING과 다름
        BookCatalogOutboxEventRecord saved = outboxRepository.save(createRecord(22L, OutboxEventRecordStatus.NEW, 5));
        String errorMessage = "Kafka Publish Failed";

        // when
        int updatedCount = outboxRepository.failAndIncrementIfCurrent(
                saved.getEventId(),
                OutboxEventRecordStatus.PUBLISHING, // from 조건
                OutboxEventRecordStatus.FAILED,
                errorMessage
        );

        // then
        assertThat(updatedCount).isEqualTo(0);

        // DB에서 레코드를 다시 조회하여, 값이 전혀 변경되지 않았는지 검증
        BookCatalogOutboxEventRecord notUpdatedRecord = outboxRepository.findByEventId(saved.getEventId()).get();
        assertThat(notUpdatedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.NEW); // 상태 그대로
        assertThat(notUpdatedRecord.getLastError()).isNull(); // 에러 메시지 없음
        assertThat(notUpdatedRecord.getRetryCount()).isEqualTo(5); // 재시도 횟수 그대로
    }

    @Test
    @DisplayName("toDeadLetterIfCurrent: 주어진 조건에 맞으면 상태를 DEAD_LETTER로 변경하고 에러 메시지를 기록한다")
    void toDeadLetterIfCurrent_성공() {
        // given
        BookCatalogOutboxEventRecord saved = outboxRepository.save(createRecord(21L, OutboxEventRecordStatus.FAILED, 10));
        String errorMessage = "Retries exhausted";

        // when
        int updatedCount = outboxRepository.toDeadLetterIfCurrent(
                saved.getEventId(),
                List.of(OutboxEventRecordStatus.FAILED, OutboxEventRecordStatus.PUBLISHING), // from 조건
                OutboxEventRecordStatus.DEAD_LETTER, // to 조건
                errorMessage
        );

        // then
        assertThat(updatedCount).isEqualTo(1);
        BookCatalogOutboxEventRecord updatedRecord = outboxRepository.findByEventId(saved.getEventId()).get();
        assertThat(updatedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.DEAD_LETTER);
        assertThat(updatedRecord.getLastError()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("toDeadLetterIfCurrent: from 상태 리스트에 포함되지 않으면 아무것도 변경하지 않는다")
    void toDeadLetterIfCurrent_실패_상태불일치() {
        // given
        // 상태가 NEW인 레코드를 저장. from 조건인 [FAILED, PUBLISHING]에 포함되지 않음
        BookCatalogOutboxEventRecord saved = outboxRepository.save(createRecord(23L, OutboxEventRecordStatus.NEW, 10));
        String errorMessage = "Retries exhausted";

        // when
        int updatedCount = outboxRepository.toDeadLetterIfCurrent(
                saved.getEventId(),
                List.of(OutboxEventRecordStatus.FAILED, OutboxEventRecordStatus.PUBLISHING), // from 조건
                OutboxEventRecordStatus.DEAD_LETTER,
                errorMessage
        );

        // then
        assertThat(updatedCount).isEqualTo(0);
        BookCatalogOutboxEventRecord notUpdatedRecord = outboxRepository.findByEventId(saved.getEventId()).get();
        assertThat(notUpdatedRecord.getOutboxEventRecordStatus()).isEqualTo(OutboxEventRecordStatus.NEW); // 상태 그대로
        assertThat(notUpdatedRecord.getLastError()).isNull(); // 에러 메시지 없음
    }

    private BookCatalogOutboxEventRecord createRecord(Long eventId, OutboxEventRecordStatus status, int retryCount) {
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
                .occurredAt(LocalDateTime.now())
                .routing(OutboxRouting.builder().topic("test-topic").partitionKey("key").build())
                .build();
    }
}

