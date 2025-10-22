package msa.bookloan.adapter.out.persistence.outbox.repository;

import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.events.outbox.dto.OutboxRouting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRecordRepositoryTest {

    @Autowired
    private OutboxEventRecordRepository outboxRepository;

    @Autowired
    private LoanSagaRepository sagaRepository;

    // 테스트 헬퍼용 상수
    private final int MAX_RETRY = 3;
    private final int LIMIT = 10;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAllInBatch();
        sagaRepository.deleteAllInBatch();
    }

    @Nested
    class Describe_upsertOutbox {

        @Test
        @DisplayName("새로운 이벤트가 주어지면 성공적으로 삽입하고 1을 반환한다")
        void upsertOutbox_insertNew() {
            // given
            long id = 1L;
            long eventId = 100L;

            // when
            int affectedRows = outboxRepository.upsertOutbox(
                    id, eventId, "TestEvent", "agg-1", "TestAggregate", 1L,
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
                    id1, eventId, "TestEvent", "agg-1", "TestAggregate", 1L,
                    "{}", "test-topic", "key-1", LocalDateTime.now()
            );

            // when
            int affectedRows = outboxRepository.upsertOutbox( // 두 번째 삽입 (중복)
                    id2, eventId, "TestEvent", "agg-1", "TestAggregate", 1L,
                    "{}", "test-topic", "key-1", LocalDateTime.now()
            );

            // then
            assertThat(affectedRows).isEqualTo(0);
            assertThat(outboxRepository.count()).isEqualTo(1); // 여전히 1개
            assertThat(outboxRepository.findById(id1)).isPresent(); // id1이 살아있음
            assertThat(outboxRepository.findById(id2)).isNotPresent(); // id2는 삽입 안 됨
        }
    }

    @Nested
    class Describe_lockClaimableIds {

        // 테스트 케이스에서 공통으로 사용할 시간 변수
        private final LocalDateTime now = LocalDateTime.now();
        // 'NEW' 상태의 이벤트가 발행되기까지 대기하는 유예 시간(10초 전)
        private final LocalDateTime grace = now.minusSeconds(10);
        // 'PUBLISHING' 상태의 이벤트가 비정상(stale)으로 간주되는 시간(5분 전)
        private final LocalDateTime stale = now.minusMinutes(5);

        @Test
        @DisplayName("'NEW' 상태고 grace 기간이 지난 이벤트를 가져온다")
        void findsNewAndPastGrace() {
            // given
            saveTestRecord(1L, OutboxEventRecordStatus.NEW, now.minusSeconds(11), 0);

            // when
            List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now, grace, stale);

            // then
            assertThat(ids).containsExactly(1L);
        }

        @Test
        @DisplayName("'NEW' 상태지만 grace 기간이 지나지 않으면 가져오지 않는다")
        void ignoresNewInGrace() {
            // given
            saveTestRecord(1L, OutboxEventRecordStatus.NEW, now.minusSeconds(5), 0);

            // when
            List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now, grace, stale);

            // then
            assertThat(ids).isEmpty();
        }

        @Test
        @DisplayName("'FAILED' 상태고 재시도 횟수가 남으면 가져온다")
        void findsFailedWithRetriesLeft() {
            // given
            saveTestRecord(1L, OutboxEventRecordStatus.FAILED, now.minusSeconds(11), 1);

            // when
            List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now, grace, stale);

            // then
            assertThat(ids).containsExactly(1L);
        }

        @Test
        @DisplayName("'FAILED' 상태고 재시도 횟수가 초과하면 무시한다")
        void ignoresFailedWithMaxRetries() {
            // given
            saveTestRecord(1L, OutboxEventRecordStatus.FAILED, now.minusSeconds(11), MAX_RETRY);

            // when
            List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now, grace, stale);

            // then
            assertThat(ids).isEmpty();
        }

        @Test
        @DisplayName("'PUBLISHING' 상태고 lease가 만료되면 가져온다")
        void findsPublishingWithLeaseExpired() {
            // given
            OutboxEventRecord record = saveTestRecord(1L, OutboxEventRecordStatus.PUBLISHING, now.minusMinutes(1), 0);

            record.setLeaseUntil(now.minusSeconds(1));
            record.setPickedAt(now.minusMinutes(1));
            outboxRepository.saveAndFlush(record);

            // when
            List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now, grace, stale);

            // then
            assertThat(ids).containsExactly(1L);
        }

        @Test
        @DisplayName("'PUBLISHING' 상태고 pickedAt이 stale(5분 초과)이면 가져온다")
        void findsPublishingWithStalePick() {
            // given
            OutboxEventRecord record = saveTestRecord(1L, OutboxEventRecordStatus.PUBLISHING, now.minusMinutes(10), 0);
            record.setLeaseUntil(now.plusMinutes(5));
            record.setPickedAt(now.minusMinutes(6));
            outboxRepository.saveAndFlush(record);

            // when
            List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now, grace, stale);

            // then
            assertThat(ids).containsExactly(1L);
        }

        @Test
        @DisplayName("'PUBLISHING' 상태고 lease가 유효하고 stale이 아니면 무시한다")
        void ignoresPublishingWithActiveLease() {
            // given
            OutboxEventRecord record = saveTestRecord(1L, OutboxEventRecordStatus.PUBLISHING, now.minusMinutes(2), 0);
            record.setLeaseUntil(now.plusMinutes(5));
            record.setPickedAt(now.minusMinutes(1));
            outboxRepository.saveAndFlush(record);

            // when
            List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now, grace, stale);

            // then
            assertThat(ids).isEmpty();
        }


        @Test
        @DisplayName("LoanSaga 이벤트는 Saga가 'PROCESSING'일 때만 가져온다")
        void findsSagaEventOnlyIfSagaIsProcessing() {
            // given
            // 사가 1: PROCESSING 상태 -> 가져와야 함
            saveTestSaga("saga-1", SagaStatus.PROCESSING); // 쿼리가 String을 사용하므로 String
            saveSagaRecord(1L, "saga-1", OutboxEventRecordStatus.NEW, now.minusSeconds(11));

            // 사가 2: FAILED 상태 -> 무시해야 함
            saveTestSaga("saga-2", SagaStatus.FAILED); // 쿼리가 String을 사용하므로 String
            saveSagaRecord(2L, "saga-2", OutboxEventRecordStatus.NEW, now.minusSeconds(11));

            // 사가 3: 일반 이벤트 (Saga 아님) -> 가져와야 함
            saveTestRecord(3L, OutboxEventRecordStatus.NEW, now.minusSeconds(11), 0);


            // when
            List<Long> ids = outboxRepository.lockClaimableIds(LIMIT, MAX_RETRY, now, grace, stale);

            // then
            assertThat(ids).containsExactlyInAnyOrder(1L, 3L);
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
                    .aggregateId("agg-" + id)
                    .aggregateType("TestAggregate")
                    .eventType("TestEvent")
                    .payload("{}")
                    .routing(new OutboxRouting("test-topic", "key-" + id))
                    .isNew(true) // Persistable용도 (이미 설정되어있지면 명시적으로)
                    .build();
            return outboxRepository.saveAndFlush(record);
        }

        private OutboxEventRecord saveSagaRecord(
                long id,
                String sagaId,
                OutboxEventRecordStatus status,
                LocalDateTime occurredAt
        ) {
            OutboxEventRecord record = OutboxEventRecord.builder()
                    .id(id)
                    .eventId(id)
                    .aggregateId(sagaId)         // Setter 대신 Builder에서 직접 설정
                    .aggregateType("LoanSaga")   // Setter 대신 Builder에서 직접 설정
                    .outboxEventRecordStatus(status)
                    .occurredAt(occurredAt)
                    .retryCount(0)
                    .eventType("TestSagaEvent")
                    .payload("{}")
                    .routing(new OutboxRouting("saga-topic", sagaId)) // @Embedded 객체 생성
                    .isNew(true) // Persistable을 위해
                    .build();
            return outboxRepository.saveAndFlush(record);
        }

        private LoanSaga saveTestSaga(String sagaId, SagaStatus status) {
            // @Builder는 Enum 타입을 직접 받습니다.
            LoanSaga saga = LoanSaga.builder()
                    .sagaId(sagaId)
                    .loanId(Long.parseLong(sagaId.replace("saga-", ""))) // 편의상
                    .memberId(123L)
                    .bookId(456L)
                    .aggregateVersion(1L) // @Builder에 필요 (필수 필드)
                    .triggerEventId(1L)   // @Builder에 필요 (필수 필드)
                    .status(status) // Enum 타입 그대로 전달
                    .currentStep(LoanSagaStep.MEMBER_CHECKING) // Enum 타입 그대로 전달
                    .build();
            return sagaRepository.saveAndFlush(saga);
        }
    }
}