package msa.bookloan.config;

import lombok.extern.slf4j.Slf4j;
import msa.common.exception.BusinessNotRetryableException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class KafkaErrorHandlerConfig {

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public DeadLetterPublishingRecoverer dlqRecoverer(KafkaTemplate<String, ?> kafkaTemplate) {
        // DLQ 토픽을 "<원본토픽>.DLQ"로 고정
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
        );
    }

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        // 재시도 2회, 1초 간격
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 2));
        // 재시도 안하도록 등록
        errorHandler.addNotRetryableExceptions(BusinessNotRetryableException.class);

        // 공통 로깅
        errorHandler.setRetryListeners((record, ex, attempt) ->
                log.error("Kafka 처리 {}차 실패: topic={}, partition={}, offset={}, error={}",
                        attempt, record.topic(), record.partition(), record.offset(), ex.getMessage())
        );
        return errorHandler;
    }
}
