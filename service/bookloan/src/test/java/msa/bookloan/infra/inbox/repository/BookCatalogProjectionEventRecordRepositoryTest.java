package msa.bookloan.infra.inbox.repository;

import msa.bookloan.config.KafkaConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = KafkaConsumerConfig.class),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "msa\\.bookloan\\.infra\\.kafka\\..*") // 리스너 등 패키지 통째 배제
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // h2로 교체 금지
class BookCatalogProjectionEventRecordRepositoryTest {

    @Autowired
    private BookCatalogProjectionEventRecordRepository projectionEventRecordRepository;

    @Test
    void upsert_increases_seenCount_on_duplicate_eventId() {
        long id1 = 1L, id2 = 2L; // pk값을 같게 주면 event_id 검증이 애매하므로 다르게 해서 event_id 충돌 명확히 검증
        long eventId = 1001L;
        long aggId = 42L; long aggVer = 7L;

        // 1) 최초 INSERT
        projectionEventRecordRepository.upsertInbox(id1, eventId, aggId, aggVer, "UPDATED", "{}", "topicA", 0, 10L);
        BookCatalogProjectionInboxEventRecord eventRecord = projectionEventRecordRepository.findByEventId(eventId).orElseThrow();
        assertThat(eventRecord.getSeenCount()).isEqualTo(1);
        assertThat(eventRecord.getId()).isEqualTo(id1);

        // 2) 다른 pk, 같은 eventId로 다시 → seen_count + 1
        projectionEventRecordRepository.upsertInbox(id2, eventId, aggId, aggVer, "UPDATED", "{}", "topicA", 0, 11L);
        BookCatalogProjectionInboxEventRecord eventRecord2 = projectionEventRecordRepository.findByEventId(eventId).orElseThrow();
        assertThat(eventRecord2.getSeenCount()).isEqualTo(2);
        assertThat(eventRecord2.getId()).isEqualTo(id1); // 기존 pk값 유지

    }
}