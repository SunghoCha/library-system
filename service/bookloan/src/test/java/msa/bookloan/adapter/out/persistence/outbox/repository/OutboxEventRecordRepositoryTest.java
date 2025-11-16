package msa.bookloan.adapter.out.persistence.outbox.repository;

import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.bookloan.testsupport.MySqlIntegrationTestBase;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.dto.OutboxRouting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static msa.common.events.outbox.OutboxEventRecordStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "app.kafka.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRecordRepositoryTest extends MySqlIntegrationTestBase {

    @Autowired
    private OutboxEventRecordRepository outboxRepository;

    @Autowired
    private LoanSagaRepository sagaRepository;

    private final int MAX_RETRY = 3;
    private final int LIMIT = 10;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAllInBatch();
        sagaRepository.deleteAllInBatch();
    }


    @Test
    @DisplayName("새로운 이벤트가 주어지면 성공적으로 삽입하고 1을 반환한다")
    void upsertOutbox_insertNew() {
        // given
        long id = 1L;
        long eventId = 100L;

        // when
        int affectedRows = outboxRepository.upsertOutbox(
                id, eventId, "TestEvent", 99L, "TestAggregate", 1L,
                "{}", "test-topic", "key-1", LocalDateTime.now()
        );

        // then
        assertThat(affectedRows).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("이미 존재하는 eventId가 주어지면 삽입을 무시하고 0을 반환한다")
    void upsertOutbox_ignoreDuplicate() {
        // given
        long id1 = 1L;
        long id2 = 2L; // id는 다르지만
        long eventId = 100L; // eventId는 동일 (유니크)

        outboxRepository.upsertOutbox( // 첫 번째 삽입
                id1, eventId, "TestEvent", 99L, "TestAggregate", 1L,
                "{}", "test-topic", "key-1", LocalDateTime.now()
        );

        // when
        outboxRepository.upsertOutbox( // 두 번째 삽입 (중복)
                id2, eventId, "TestEvent", 99L, "TestAggregate", 1L,
                "{}", "test-topic", "key-1", LocalDateTime.now()
        );

        // then
        assertThat(outboxRepository.count()).isEqualTo(1); // 여전히 1개
        assertThat(outboxRepository.findById(id1)).isPresent(); // id1이 살아있음
        assertThat(outboxRepository.findById(id2)).isNotPresent(); // id2는 삽입 안 됨
    }


    // 테스트 케이스에서 공통으로 사용할 시간 변수. 굳이 clock 받아서해야하는건지 고민
    private final LocalDateTime now = LocalDateTime.now();

    @Test
    @DisplayName("NEW 상태의 레코드는 생성 시간과 관계없이 즉시 조회된다")
    void findsNewRecordsImmediately() {
        // given
        saveTestRecord(1L, NEW, now.minusSeconds(1), 0);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).containsExactly(1L);
    }

    @Test
    @DisplayName("FAILED 상태고 재시도 횟수가 남으면 가져온다")
    void findsFailedWithRetriesLeft() {
        // given
        saveTestRecord(1L, FAILED, now.minusSeconds(11), 1);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).containsExactly(1L);
    }

    @Test
    @DisplayName("FAILED 상태고 재시도 횟수가 초과하면 무시한다")
    void ignoresFailedWithMaxRetries() {
        // given
        saveTestRecord(1L, FAILED, now.minusSeconds(11), MAX_RETRY);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("'PUBLISHING' 상태고 leaseUntil이 NULL이면 조회된다 (비정상 종료 복구)")
    void findsPublishingWithNullLease() {
        saveTestRecord(1L, PUBLISHING, now.minusMinutes(10), 0);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).containsExactly(1L);
    }

    @Test
    @DisplayName("PUBLISHING 상태고 lease가 만료되면 가져온다")
    void findsPublishingWithLeaseExpired() {
        // given
        OutboxEventRecord record = saveTestRecord(1L, PUBLISHING, now.minusMinutes(1), 0);

        record.setLeaseUntil(now.minusSeconds(1));
        outboxRepository.save(record);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).containsExactly(1L);
    }

    @Test
    @DisplayName("PUBLISHING 상태고 lease가 만료되면 가져온다")
    void findsPublishingWithExpiredLease() {
        // given
        OutboxEventRecord record = saveTestRecord(1L, PUBLISHING, now.minusMinutes(10), 0);
        record.setLeaseUntil(now.minusMinutes(5));
        outboxRepository.save(record);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).containsExactly(1L);
    }

    @Test
    @DisplayName("PUBLISHING 상태고 lease_until이 미래(유효)면 무시된다")
    void ignoresPublishingWithValidLease() {
        // given
        OutboxEventRecord record = saveTestRecord(1L, PUBLISHING, now.minusMinutes(2), 0);
        record.setLeaseUntil(now.plusMinutes(5)); // 5분 뒤 만료
        outboxRepository.save(record);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).isEmpty();
    }


    @Test
    @DisplayName("PROCESSED 또는 DEAD_LETTER 상태는 무시된다")
    void ignoresTerminalStatuses() {
        // given
        saveTestRecord(1L, OutboxEventRecordStatus.PUBLISHED, now.minusMinutes(10), 0);
        saveTestRecord(2L, OutboxEventRecordStatus.DEAD_LETTER, now.minusMinutes(10), 0);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("Saga 상태(FAILED)와 관계없이 NEW 상태의 이벤트는 모두 조회된다")
    void findsNewSagaEventRegardlessOfSagaState() {
        // given
        // saga PROCESSING + NEW
        saveTestSaga(123L, SagaStatus.PROCESSING);
        saveSagaRecord(1L, 123L, OutboxEventRecordStatus.NEW, now.minusSeconds(11));

        // saga FAILED + NEW
        saveTestSaga(456L, SagaStatus.FAILED);
        saveSagaRecord(2L, 456L, OutboxEventRecordStatus.NEW, now.minusSeconds(10));

        // domain + NEW
        saveTestRecord(3L, OutboxEventRecordStatus.NEW, now.minusSeconds(9), 0);

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now);

        // then
        assertThat(ids).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("occurred_at 순서(오래된 순) 및 LIMIT = 2에 맞게 조회한다")
    void findsOrderedAndLimited() {
        // given 순서 섞어서 저장
        saveTestRecord(1L, OutboxEventRecordStatus.NEW, now.minusSeconds(5), 0); // 3순위
        saveTestRecord(2L, OutboxEventRecordStatus.NEW, now.minusSeconds(10), 0); // 1순위
        saveTestRecord(3L, OutboxEventRecordStatus.NEW, now.minusSeconds(8), 0); // 2순위

        // when
        List<Long> ids = outboxRepository.lockClaimableIds(2, MAX_RETRY, now); // LIMIT = 2

        // then
        assertThat(ids).containsExactly(2L, 3L);
    }

    private OutboxEventRecord saveTestRecord(
            long id,
            OutboxEventRecordStatus status, // Enum 타입 사용
            LocalDateTime occurredAt,
            int retryCount
    ) {
        OutboxEventRecord record = OutboxEventRecord.builder()
                .id(id)
                .eventId(id)
                .outboxEventRecordStatus(status)
                .occurredAt(occurredAt)
                .retryCount(retryCount)
                .aggregateId(99L)
                .aggregateType("TestAggregate")
                .eventType("TestEvent")
                .payload("{}")
                .routing(new OutboxRouting("test-topic", "key-" + id))
                .build();
        return outboxRepository.saveAndFlush(record);
    }

    private OutboxEventRecord saveSagaRecord(
            Long id,
            Long sagaId,
            OutboxEventRecordStatus status,
            LocalDateTime occurredAt
    ) {
        OutboxEventRecord record = OutboxEventRecord.builder()
                .id(id)
                .eventId(id)
                .aggregateId(sagaId)
                .aggregateType("LoanSaga")
                .outboxEventRecordStatus(status)
                .occurredAt(occurredAt)
                .retryCount(0)
                .eventType("TestSagaEvent")
                .payload("{}")
                .routing(new OutboxRouting("saga-topic", String.valueOf(sagaId))) // @Embedded 객체 생성
                .build();
        return outboxRepository.saveAndFlush(record);
    }

    private LoanSaga saveTestSaga(Long sagaId, SagaStatus status) {
        LoanSaga saga = LoanSaga.builder()
                .id(sagaId)
                .loanId(12345L)
                .memberId(123L)
                .bookId(456L)
                .aggregateVersion(1L)
                .triggerEventId(1L)
                .status(status)
                .currentStep(LoanSagaStep.MEMBER_CHECKING)
                .build();
        return sagaRepository.saveAndFlush(saga);
    }
}
