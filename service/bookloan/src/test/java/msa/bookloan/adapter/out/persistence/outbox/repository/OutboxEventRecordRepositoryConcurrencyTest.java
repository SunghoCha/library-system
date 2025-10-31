//package msa.bookloan.adapter.out.persistence.outbox.repository;
//
//
//import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
//import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
//import msa.common.events.outbox.OutboxEventRecordStatus;
//import msa.common.events.outbox.dto.OutboxRouting;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.transaction.support.TransactionTemplate;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@DataJpaTest
//@Testcontainers
//@Transactional(propagation = Propagation.NOT_SUPPORTED)
//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//public class OutboxEventRecordRepositoryConcurrencyTest {
//
//    @Autowired
//    private OutboxEventRecordRepository outboxRepository;
//
//    @Autowired
//    private LoanSagaRepository sagaRepository;
//
//    @Autowired
//    private TransactionTemplate transactionTemplate;
//
//    private final int MAX_RETRY = 3;
//    private final int LIMIT = 10;
//
//    //@BeforeEach
//    void setUp() {
//        outboxRepository.deleteAllInBatch();
//        sagaRepository.deleteAllInBatch();
//    }
//
//    @Test
//    @DisplayName("두 스레드가 동시에 호출하면 락을 스킵하고 서로 다른 이벤트를 가져간다 (SKIP LOCKED)")
//    void concurrentAccessSkipsLockedRows() throws InterruptedException {
//        // given
//
//        final LocalDateTime now = LocalDateTime.now();
//        final LocalDateTime grace = now.plusSeconds(10);
//        final LocalDateTime stale = now.minusMinutes(5);
//        // setup 코드를 별도 트랜잭션으로 묶어 즉시 커밋
//        transactionTemplate.execute(status -> {
//
//            // @BeforeEach의 작업을 여기서 "다시" 수행 (커밋을 위해)
//            outboxRepository.deleteAllInBatch();
//            sagaRepository.deleteAllInBatch();
//
//            // 테스트 데이터 삽입
//            saveTestRecord(1L, OutboxEventRecordStatus.NEW, now.minusSeconds(11), 0);
//            saveTestRecord(2L, OutboxEventRecordStatus.NEW, now.minusSeconds(11), 0);
//            return null;
//        });
//        // 이 시점에 1L, 2L 레코드는 DB에 커밋되어 모든 스레드에서 보입니다.
//
//        int threadCount = 2;
//        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch latch = new CountDownLatch(threadCount);
//
//        List<Long> thread1Result = new java.util.ArrayList<>();
//        List<Long> thread2Result = new java.util.ArrayList<>();
//
//        // Thread1
//        executor.submit(() -> {
//            // 스레드 1의 작업을 별도 트랜잭션(Tx-1)으로 묶음
//            transactionTemplate.execute(status -> {
//                List<Long> ids = outboxRepository.lockClaimableIds(1, MAX_RETRY, now, grace, stale);
//                if (!ids.isEmpty()) {
//                    thread1Result.add(ids.get(0));
//                    try {
//                        Thread.sleep(2000); // Tx-1이 락을 잡고 2초 대기
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                    }
//                }
//                latch.countDown();
//                return null;
//            }); // Tx-1 커밋/롤백
//        });
//
//        // Thread2
//        executor.submit(() -> {
//            try {
//                Thread.sleep(500); // Tx-1이 락을 잡을 시간
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//            // 스레드 2의 작업을 별도 트랜잭션(Tx-2)으로 묶음
//            transactionTemplate.execute(status -> {
//                // Tx-1이 1L(또는 2L)을 락 잡고 있으므로, SKIP LOCKED에 의해 나머지 레코드를 가져옴
//                List<Long> ids = outboxRepository.lockClaimableIds(1, MAX_RETRY, now, grace, stale);
//                if (!ids.isEmpty()) {
//                    thread2Result.add(ids.get(0));
//                }
//                latch.countDown();
//                return null;
//            }); // Tx-2 커밋/롤백
//        });
//
//        // when
//        boolean completed = latch.await(10, TimeUnit.SECONDS);
//        executor.shutdown();
//
//        // then
//        assertThat(completed).isTrue();
//        assertThat(thread1Result).hasSize(1); // 이제 1L(또는 2L)을 가져옴
//        assertThat(thread2Result).hasSize(1); // 이제 2L(또는 1L)을 가져옴
//        assertThat(thread1Result.get(0)).isNotEqualTo(thread2Result.get(0));
//        assertThat(List.of(1L, 2L)).contains(thread1Result.get(0), thread2Result.get(0));
//    }
//
//    private OutboxEventRecord saveTestRecord(
//            long id,
//            OutboxEventRecordStatus status, // Enum 타입 사용
//            LocalDateTime occurredAt,
//            int retryCount
//    ) {
//        OutboxEventRecord record = OutboxEventRecord.builder()
//                .id(id)
//                .eventId(id)
//                .outboxEventRecordStatus(status)
//                .occurredAt(occurredAt)
//                .retryCount(retryCount)
//                .aggregateId("agg-" + id)
//                .aggregateType("TestAggregate")
//                .eventType("TestEvent")
//                .payload("{}")
//                .routing(new OutboxRouting("test-topic", "key-" + id))
//                .isNew(true) // Persistable용도 (이미 설정되어있지면 명시적으로)
//                .build();
//        return outboxRepository.saveAndFlush(record);
//    }
//}
