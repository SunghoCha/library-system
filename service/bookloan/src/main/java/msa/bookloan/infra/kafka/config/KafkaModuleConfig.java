package msa.bookloan.infra.kafka.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true",
        matchIfMissing = false // 기본 OFF
)
@EnableKafka
@ComponentScan(basePackages = {
        "msa.bookloan.infra.kafka" // producer, consumer, listener, factory 등
})
public class KafkaModuleConfig { }

