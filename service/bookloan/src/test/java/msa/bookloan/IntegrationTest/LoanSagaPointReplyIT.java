package msa.bookloan.IntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.adapter.in.messaging.inbox.scheduler.InboxPollingScheduler;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
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
import msa.common.events.bookloan.saga.reply.point.PointChargedReply;
import msa.common.events.outbox.OutboxEventRecordStatus;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "app.kafka.listeners.saga-replies.enabled=true",
})
public class LoanSagaPointReplyIT extends KafkaTestBase {

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
    @DisplayName("Saga 응답(PointCharged) 수신 시 Saga 상태가 SHIPPING_SCHEDULING으로 전이되고 다음 커맨드가 발행된다")
    void testSagaHappyPathNextStep() throws Exception {

        // given
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
                .currentStep(LoanSagaStep.POINT_CHARGING)
                .status(SagaStatus.PROCESSING)
                .version(1L)
                .build();
        loanSagaRepository.save(saga);

        // when
        // 포인트 서비스가 보낸 성공 응답 메시지 생성
        String fakeReplyJson = createFakePointChargedEvent(sagaId, loanId, memberId);

        // Kafka로 성공 응답 발행
        kafkaTemplate.send(REPLY_TOPIC, String.valueOf(sagaId), fakeReplyJson);

        // await (처리)
        await().atMost(5, SECONDS).untilAsserted(() -> {
            inboxPollingScheduler.pollAndProcess();

            // 검증: Saga 상태가 배송 예약 중(SHIPPING_SCHEDULING)으로 변경되었는지 확인
            LoanSaga changedSaga = loanSagaRepository.findById(sagaId).orElseThrow();
            assertThat(changedSaga.getCurrentStep())
                    .isEqualTo(LoanSagaStep.SHIPPING_SCHEDULING);
        });

        // then
        LoanSaga finalSaga = loanSagaRepository.findById(sagaId).orElseThrow();
        assertThat(finalSaga.getCurrentStep()).isEqualTo(LoanSagaStep.SHIPPING_SCHEDULING);
        assertThat(finalSaga.getStatus()).isEqualTo(SagaStatus.PROCESSING);

        // 검증: Outbox에 SHIPPING_SCHEDULE (배송 예약) 커맨드가 저장되었는지 확인
        Optional<OutboxEventRecord> nextCommand = outboxEventRecordRepository.findAll()
                .stream()
                .filter(rec -> rec.getAggregateId().equals(sagaId) &&
                        // 다음 단계 커맨드(SHIPPING_SCHEDULE) 검증
                        rec.getEventType().equals(SagaCommandType.SHIPPING_SCHEDULE.getValue()) &&
                        rec.getOutboxEventRecordStatus() == OutboxEventRecordStatus.NEW)
                .findFirst();

        assertThat(nextCommand).isPresent();
    }

    private String createFakePointChargedEvent(Long sagaId, Long loanId, Long memberId) throws Exception {

        PointChargedReply replyPayload = new PointChargedReply(
                String.valueOf(snowflake.nextId()),
                String.valueOf(sagaId),
                String.valueOf(snowflake.nextId()),
                1L,
                String.valueOf(memberId),
                100L,
                String.valueOf(snowflake.nextId())
        );

        JsonNode payloadNode = objectMapper.valueToTree(replyPayload);

        // MessageEnvelope 생성
        MessageEnvelope envelope = new MessageEnvelope(
                replyPayload.eventId(),
                String.valueOf(loanId),
                replyPayload.loanVersion(),
                SagaReplyType.POINT_CHARGED.getValue(),
                payloadNode
        );

        // JSON 문자열로 직렬화
        return objectMapper.writeValueAsString(envelope);
    }
}
