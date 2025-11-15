package msa.bookloan.IntegrationTestV2;

import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.web.controller.dto.request.LoanCreateRequest;
import msa.bookloan.adapter.out.messaging.outbox.scheduler.OutboxPollingScheduler;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.application.service.LoanService;
import msa.bookloan.testsupport.DatabaseClearExtension;
import msa.bookloan.testsupport.IntegrationTestBaseV2;
import msa.common.domain.model.MemberGrade;
import msa.common.events.MessageEnvelope;
import msa.common.events.bookloan.saga.command.SagaCommandType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static msa.bookloan.IntegrationTestV2.OutboxPublishIT.TestConsumers.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@Slf4j
@Import(IntegrationTestBaseV2.KafkaTopics.class)
@ExtendWith(DatabaseClearExtension.class)
@SpringBootTest(properties = {
        "outbox.relay.enabled=true",
        "app.kafka.enabled=true",
        "app.kafka.listeners.catalog.enabled=false",
})
//@Import({
//        msa.bookloan.infra.config.kafka.KafkaConsumerConfig.class,
//        msa.bookloan.infra.config.kafka.KafkaProducerConfig.class,
//        OutboxPublishIT.TestConsumers.class,
//})
public class OutboxPublishIT extends IntegrationTestBaseV2 {

    @Autowired
    private LoanService loanService;

    @Autowired
    private OutboxPollingScheduler outboxPollingScheduler;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @MockBean
    MemberPort memberPort;

    @BeforeEach
    void setUpAndWaitForListener() {
        when(memberPort.getGrade(anyLong())).thenReturn(MemberGrade.SILVER);

        MessageListenerContainer container = registry.getListenerContainer("testOutboxListener");
        if (container == null) throw new IllegalStateException("listener not found");

        log.info("Found listener container: {}", container);

        // 컨테이너의 Group ID (어떤 컨슈머 그룹인지 확인)
        log.info("Container Group ID: {}", container.getGroupId());

        // start() 호출 전 현재 실행 상태 확인 (아마도 false)
        log.info("Container isRunning() before start: {}", container.isRunning());

        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);
    }


    @AfterEach
    void tearDown() {
        registry.getListenerContainers().forEach(Lifecycle::stop);
    }

    @TestConfiguration
    static class TestConsumers {

        static final BlockingQueue<MessageEnvelope> RECEIVED = new LinkedBlockingQueue<>();

        // 리스너
        @KafkaListener(
                id = "testOutboxListener",
                topics = "${app.kafka.topic-inventory-reserve}",
                groupId = "test-outbox-publish")
        void onReserve(MessageEnvelope envelope) {
            RECEIVED.offer(envelope); // 신호 + 데이터 전달을 한 번에
        }
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
