package msa.bookloan.IntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.adapter.in.messaging.inbox.scheduler.InboxPollingScheduler;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.bookloan.testsupport.KafkaTestBase;
import msa.common.events.MessageEnvelope;
import msa.common.events.bookloan.saga.command.SagaCommandType;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReservedReply;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "app.kafka.listeners.saga-replies.enabled=true",
})
public class LoanSagaReplyIT extends KafkaTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private LoanSagaRepository loanSagaRepository;

    @Autowired
    private OutboxEventRecordRepository outboxEventRecordRepository;

    @Autowired
    private InboxPollingScheduler inboxPollingScheduler;

    @Autowired
    Snowflake snowflake;

    @MockBean
    MemberPort memberPort;

    @Value("${app.kafka.topic-saga-replies}")
    private String REPLY_TOPIC;

    @Test
    @DisplayName("Saga 응답(InventoryReserved) 수신 시 Saga 상태가 전이되고 다음 커맨드가 발행된다")
    void testSagaReplyFlow() throws Exception {
        // given
        // 테스트할 Saga 데이터를 'INVENTORY_RESERVING' 상태로 DB에 미리 저장
        long sagaId = 1000L;
        long loanId = 2000L;
        long memberId = 1L;
        long bookId = 100L;
        long triggerEventId = 9999L;

        LoanSaga saga = LoanSaga.builder()
                .id(sagaId)
                .loanId(loanId)
                .memberId(memberId)
                .bookId(bookId)
                .triggerEventId(triggerEventId)
                .currentStep(LoanSagaStep.INVENTORY_RESERVING)
                .status(SagaStatus.PROCESSING)
                .version(0L) // (초기 버전)
                .build();
        loanSagaRepository.save(saga);

        // when
        // 재고 서비스가 보낸 더미 응답 메시지
        String fakeReplyJson = createFakeInventoryReservedEvent(sagaId, loanId, bookId);
        kafkaTemplate.send(REPLY_TOPIC, String.valueOf(sagaId), fakeReplyJson);

        // Kafka 리스너가 메시지를 받아 인박스 테이블에 저장할 시간 1~2초 정도 대기
        // 인박스 스케줄러가 인박스 테이블에서 폴링
        await().atMost(2, SECONDS).untilAsserted(() -> {
            // Inbox 스케줄러를 반복적으로 실행
            inboxPollingScheduler.pollAndProcess();

            // Saga 상태가 다음 단계(POINT_CHARGING)로 변경되었는지 확인
            LoanSaga changedSaga = loanSagaRepository.findById(sagaId).orElseThrow();
            assertThat(changedSaga.getCurrentStep())
                    .isEqualTo(LoanSagaStep.POINT_CHARGING);
        });

        // then
        LoanSaga finalSaga = loanSagaRepository.findById(sagaId).orElseThrow();
        assertThat(finalSaga.getCurrentStep()).isEqualTo(LoanSagaStep.POINT_CHARGING);

        // DB에서 아웃박스 테이블에 PointCharged 커맨드 새로 저장되었는지 확인
        boolean exists = outboxEventRecordRepository.findAll()
                .stream()
                .anyMatch(r -> r.getAggregateId().equals(sagaId) &&
                        r.getEventType().equals(SagaCommandType.POINT_CHARGE.getValue()) &&
                        r.getOutboxEventRecordStatus() == OutboxEventRecordStatus.NEW);

        assertThat(exists).isTrue();
    }

    private String createFakeInventoryReservedEvent(Long sagaId, Long loanId, Long bookId) throws Exception {
        InventoryReservedReply replyPayload = new InventoryReservedReply(
                String.valueOf(snowflake.nextId()),
                String.valueOf(sagaId),
                String.valueOf(snowflake.nextId()), // causationCommandId
                0L, // loanVersion
                String.valueOf(bookId),
                String.valueOf(snowflake.nextId())  // reservationId
        );

        JsonNode payloadNode = objectMapper.valueToTree(replyPayload);

        // 2. MessageEnvelope 생성
        MessageEnvelope envelope = new MessageEnvelope(
                String.valueOf(snowflake.nextId()), // eventId
                String.valueOf(loanId),        // aggregateId (대출 ID)
                0L,                            // aggregateVersion
                SagaReplyType.INVENTORY_RESERVED.getValue(),
                payloadNode
        );

        // 3. JSON 문자열로 직렬화
        return objectMapper.writeValueAsString(envelope);
    }
}
