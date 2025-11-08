package msa.bookloan.IntegrationTest;

import msa.bookloan.adapter.in.web.controller.dto.request.LoanCreateRequest;
import msa.bookloan.adapter.out.messaging.outbox.scheduler.OutboxPollingScheduler;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.application.service.LoanService;
import msa.bookloan.testsupport.KafkaTestBase;
import msa.common.domain.model.MemberGrade;
import msa.common.events.MessageEnvelope;
import msa.common.events.bookloan.saga.command.SagaCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static msa.bookloan.IntegrationTest.OutboxPublishIT.TestConsumers.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;


@SpringBootTest(properties = {
        // 스케줄러 빈 ON (메서드는 직접 호출)
        "outbox.relay.enabled=true",
        // 카프카 모듈 ON, 잡다한 리스너는 OFF
        "app.kafka.enabled=true",
        "app.kafka.listeners.catalog.enabled=false",
        // 테스트에선 Feign OFF
        "app.clients.enabled=false",
        // 필요시 테스트 토픽 이름(이미 설정돼 있으면 생략 가능)
        // "app.kafka.topic-inventory-reserve=book-loan.requested"
})
@Import({
        msa.bookloan.infra.config.kafka.KafkaConsumerConfig.class,
        msa.bookloan.infra.config.kafka.KafkaProducerConfig.class,
        OutboxPublishIT.TestConsumers.class,
})
public class OutboxPublishIT extends KafkaTestBase {

    @Autowired
    LoanService loanService;

    @Autowired
    OutboxPollingScheduler outboxPollingScheduler;

    @MockBean
    MemberPort memberPort;

    @TestConfiguration
    static class TestConsumers {

        static final BlockingQueue<MessageEnvelope> RECEIVED = new LinkedBlockingQueue<>();

        // 리스너
        @KafkaListener(topics = "${app.kafka.topic-inventory-reserve}",
                groupId = "test-outbox-publish",
                containerFactory = "envelopeListenerFactory")
        void onReserve(MessageEnvelope envelope) {
            RECEIVED.offer(envelope); // 신호 + 데이터 전달을 한 번에
        }
    }

    @BeforeEach
    void setUp() {
        when(memberPort.getGrade(anyLong())).thenReturn(MemberGrade.SILVER);
    }

    @Test
    void createLoan_then_outbox_publishes_reserveInventory() throws Exception {
        loanService.createLoan(1L, new LoanCreateRequest(1L, 100L));
        outboxPollingScheduler.pollAndPublish();

        MessageEnvelope env = RECEIVED.poll(8, TimeUnit.SECONDS); // 타임아웃까지 대기
        assertThat(env).isNotNull();
        assertThat(env.eventType()).isEqualTo(SagaCommandType.INVENTORY_RESERVE.getValue());
        assertThat(env.payload().get("bookId").asLong()).isEqualTo(100L);
    }
}
