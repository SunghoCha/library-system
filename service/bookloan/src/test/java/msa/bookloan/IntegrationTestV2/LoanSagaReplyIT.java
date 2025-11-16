package msa.bookloan.IntegrationTestV2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.scheduler.InboxPollingScheduler;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.adapter.out.persistence.saga.repository.LoanSagaRepository;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.domain.saga.LoanSaga;
import msa.bookloan.domain.saga.LoanSagaStep;
import msa.bookloan.domain.saga.SagaStatus;
import msa.bookloan.testsupport.DatabaseClearExtension;
import msa.bookloan.testsupport.IntegrationTestBase;
import msa.bookloan.testsupport.IntegrationTestBaseV2;
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
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;


@Slf4j
@Import(IntegrationTestBaseV2.KafkaTopics.class)
@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "app.kafka.listeners.saga-replies.enabled=true",
})
public class LoanSagaReplyIT extends IntegrationTestBase {

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
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    Snowflake snowflake;

    @MockBean
    MemberPort memberPort;

    @Value("${app.kafka.topic-saga-replies}")
    private String REPLY_TOPIC;

    @BeforeEach
    void waitForKafkaAssignment() {
        MessageListenerContainer container = registry.getListenerContainer("sagaRepliesListener");
        if (container == null) throw new IllegalStateException("listener not found");
        container.start();
        log.info("container start: {}, groupId: {}", container, container.getGroupId());
        ContainerTestUtils.waitForAssignment(container, 1);
    }

    @AfterEach
    void tearDown() {
        registry.getListenerContainers().forEach(Lifecycle::stop);
    }

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
                .build();
        loanSagaRepository.save(saga);

        // when
        // 재고 서비스가 보낸 더미 응답 메시지
        String fakeReplyJson = createFakeInventoryReservedEvent(sagaId, loanId, bookId);
        kafkaTemplate.send(REPLY_TOPIC, String.valueOf(sagaId), fakeReplyJson).get(5, SECONDS);

        // Kafka 리스너가 메시지를 받아 인박스 테이블에 저장할 시간동안 짧은 대기
        // 인박스 스케줄러가 인박스 테이블에서 폴링
        await()
                .pollInterval(Duration.ofSeconds(2))
                .atMost(8, SECONDS).untilAsserted(() -> {
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

        // MessageEnvelope 생성
        MessageEnvelope envelope = new MessageEnvelope(
                String.valueOf(snowflake.nextId()), // eventId
                String.valueOf(loanId),        // aggregateId (대출 ID)
                0L,                            // aggregateVersion
                SagaReplyType.INVENTORY_RESERVED.getValue(),
                payloadNode
        );

        // JSON 문자열로 직렬화
        return objectMapper.writeValueAsString(envelope);
    }
}
