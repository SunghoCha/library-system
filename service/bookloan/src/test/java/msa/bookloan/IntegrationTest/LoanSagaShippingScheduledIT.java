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
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.shipping.ShippingScheduledReply;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "app.kafka.listeners.saga-replies.enabled=true",
})
public class LoanSagaShippingScheduledIT extends KafkaTestBase {

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
    @DisplayName("Saga 응답(ShippingScheduled) 수신 시 Saga가 FINISHED/COMPLETED 상태로 종결된다")
    void testSagaCompletionFlow() throws Exception {

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
                // given 상태: 배송 예약 중
                .currentStep(LoanSagaStep.SHIPPING_SCHEDULING)
                .status(SagaStatus.PROCESSING)
                .version(2L) // (포인트 차감까지 통과한 상태로 가정)
                .build();
        loanSagaRepository.save(saga);

        // when
        // 배송 서비스가 보낸 성공 응답 메시지 생성
        String fakeReplyJson = createFakeShippingScheduledEvent(sagaId, loanId, bookId);

        // Kafka로 성공 응답 발행
        kafkaTemplate.send(REPLY_TOPIC, String.valueOf(sagaId), fakeReplyJson);

        // await (처리)
        await().atMost(5, SECONDS).untilAsserted(() -> {
            inboxPollingScheduler.pollAndProcess();

            // 검증: Saga 상태가 완료(FINISHED)로 변경되었는지 확인
            LoanSaga changedSaga = loanSagaRepository.findById(sagaId).orElseThrow();
            assertThat(changedSaga.getCurrentStep())
                    .isEqualTo(LoanSagaStep.FINISHED);
        });

        // then (최종 검증)
        LoanSaga finalSaga = loanSagaRepository.findById(sagaId).orElseThrow();

        // 검증: Step과 Status가 모두 최종 완료 상태인지 확인
        assertThat(finalSaga.getCurrentStep()).isEqualTo(LoanSagaStep.FINISHED);
        assertThat(finalSaga.getStatus()).isEqualTo(SagaStatus.COMPLETED);

        // 검증: Outbox에 더 이상 새로운 커맨드가 발행되지 않았는지 확인
        long newCommandCount = outboxEventRecordRepository.findAll()
                .stream()
                .filter(rec -> rec.getAggregateId().equals(sagaId) &&
                        rec.getOutboxEventRecordStatus() == OutboxEventRecordStatus.NEW)
                .count();

        assertThat(newCommandCount).isZero();
    }

    private String createFakeShippingScheduledEvent(Long sagaId, Long loanId, Long bookId) throws Exception {

        ShippingScheduledReply replyPayload = new ShippingScheduledReply(
                String.valueOf(snowflake.nextId()),
                String.valueOf(sagaId),
                String.valueOf(snowflake.nextId()),
                2L,
                String.valueOf(snowflake.nextId()),
                String.valueOf(bookId),
                "TRACKING-12345"
        );

        JsonNode payloadNode = objectMapper.valueToTree(replyPayload);

        // MessageEnvelope 생성
        MessageEnvelope envelope = new MessageEnvelope(
                replyPayload.eventId(),
                String.valueOf(loanId),
                replyPayload.loanVersion(),
                // 성공 이벤트 타입 사용
                SagaReplyType.SHIPPING_SCHEDULED.getValue(),
                payloadNode
        );

        // JSON 문자열로 직렬화
        return objectMapper.writeValueAsString(envelope);
    }
}
