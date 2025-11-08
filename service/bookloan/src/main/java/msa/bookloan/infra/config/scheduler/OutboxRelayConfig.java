package msa.bookloan.infra.config.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import msa.bookloan.adapter.out.messaging.outbox.OutboxEventSender;
import msa.bookloan.adapter.out.messaging.outbox.OutboxRelayProcessor;
import msa.bookloan.adapter.out.messaging.outbox.scheduler.OutboxPollingScheduler;
import msa.bookloan.adapter.out.persistence.outbox.OutboxClaimerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;


@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = {"outbox.relay.enabled", "app.kafka.enabled"},
        havingValue = "true"
)
public class OutboxRelayConfig {

    @Bean
    public OutboxEventSender outboxEventSender(
            ObjectMapper objectMapper,
            OutboxRelayProcessor outboxRelayProcessor,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        return new OutboxEventSender(objectMapper, outboxRelayProcessor, kafkaTemplate);
    }

    @Bean
    public OutboxPollingScheduler outboxPollingScheduler(
            OutboxEventSender outboxEventSender,
            OutboxClaimerService outboxClaimerService
    ) {
        return new OutboxPollingScheduler(outboxEventSender, outboxClaimerService);
    }
}
