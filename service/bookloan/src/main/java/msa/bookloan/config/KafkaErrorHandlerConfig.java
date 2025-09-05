package msa.bookloan.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DeadLetterPublishingRecoverer dlqRecoverer(KafkaTemplate<String, ?> kafkaTemplate) {
        // DLQ 토픽을 "<원본토픽>.DLQ"로 고정
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
        );
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        // 재시도 2회, 1초 간격
        DefaultErrorHandler eh = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 2));
        // 공통 로깅
        eh.setRetryListeners((record, ex, attempt) ->
                log.error("Kafka 처리 {}차 실패: topic={}, partition={}, offset={}, error={}",
                        attempt, record.topic(), record.partition(), record.offset(), ex.getMessage())
        );
        return eh;
    }
}
