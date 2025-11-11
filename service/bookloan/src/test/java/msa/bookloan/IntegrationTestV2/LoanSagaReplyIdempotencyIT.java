package msa.bookloan.IntegrationTestV2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.adapter.in.messaging.inbox.scheduler.InboxPollingScheduler;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.bookloan.testsupport.DatabaseClearExtension;
import msa.bookloan.testsupport.KafkaTestBase;
import msa.common.events.MessageEnvelope;
import msa.common.events.bookloan.saga.command.SagaCommandType;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.inventory.InventoryReservedReply;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(KafkaTestBase.KafkaTopics.class)
@ExtendWith(DatabaseClearExtension.class)
@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "app.kafka.listeners.saga-replies.enabled=true",
})
public class LoanSagaReplyIdempotencyIT extends KafkaTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoanSagaRepository loanSagaRepository;

    @Autowired
    private OutboxEventRecordRepository outboxEventRecordRepository;

    @Autowired
    private InboxPollingScheduler inboxPollingScheduler;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    Snowflake snowflake;

    @MockBean
    MemberPort memberPort;

    @Value("${app.kafka.topic-saga-replies}")
    private String REPLY_TOPIC;

//    @BeforeEach
//    void waitForKafkaAssignment() {
//        MessageListenerContainer container = registry.getListenerContainer("sagaRepliesListener");
//        if (container == null) throw new IllegalStateException("listener not found");
//        container.start();
//        ContainerTestUtils.waitForAssignment(container, 1);
//    }
//
//    @AfterEach
//    void tearDown() {
//        MessageListenerContainer container = registry.getListenerContainer("sagaRepliesListener");
//        if (container != null && container.isRunning()) {
//            container.stop();
//        }
//    }


    @Test
    @DisplayName("중복된 응답 메시지를 수신해도 사가 상태 전이는 1번만 발생한다")
    void testIdempotencyFlow() throws Exception {
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
                .build();
        loanSagaRepository.save(saga);


        String fakeReplyJson = createFakeInventoryReservedEvent(sagaId, loanId, bookId);
        kafkaTemplate.send(REPLY_TOPIC, String.valueOf(sagaId), fakeReplyJson);
        kafkaTemplate.send(REPLY_TOPIC, String.valueOf(sagaId), fakeReplyJson);


        // 같은 메시지 두 번 발행되어도 상태는 여전히 POINT_CHARGING
        await()
                .pollInterval(Duration.ofSeconds(2))
                .atMost(8, SECONDS).untilAsserted(() -> {
            inboxPollingScheduler.pollAndProcess();
            LoanSaga changedSaga = loanSagaRepository.findById(saga.getId()).orElseThrow();
            assertThat(changedSaga.getCurrentStep()).isEqualTo(LoanSagaStep.POINT_CHARGING);
        });

        long count = outboxEventRecordRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(saga.getId()) &&
                        r.getEventType().equals(SagaCommandType.POINT_CHARGE.getValue()) &&
                        r.getOutboxEventRecordStatus().equals(OutboxEventRecordStatus.NEW))
                .count();

        assertThat(count).isEqualTo(1);
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

        MessageEnvelope envelope = new MessageEnvelope(
                String.valueOf(snowflake.nextId()), // eventId
                String.valueOf(loanId),        // aggregateId
                0L,                            // aggregateVersion
                SagaReplyType.INVENTORY_RESERVED.getValue(),
                payloadNode
        );

        return objectMapper.writeValueAsString(envelope);
    }
}
