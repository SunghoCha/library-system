package msa.bookloan.infra.config.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true",
        matchIfMissing = false // 기본 OFF
)
@EnableKafka
@Import({
        KafkaConsumerConfig.class,
        KafkaProducerConfig.class,
        KafkaErrorHandlerConfig.class,
        KafkaListenersConfig.class
})
public class KafkaModuleConfig {}

