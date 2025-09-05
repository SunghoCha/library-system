package msa.bookloan.config;

import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

import static org.springframework.kafka.listener.ContainerProperties.*;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    @Bean
    public ConsumerFactory<String, BookCatalogChangedEvent> consumerFactory() {
        HashMap<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());

        // disable auto-commit, 직접 AckMode로 제어
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // JSON 역직렬화 시 신뢰할 패키지
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "msa.common.events");
        // consumer 그룹 아이디
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "loan-service-bookinfo-replica");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(BookCatalogChangedEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookCatalogChangedEvent>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, BookCatalogChangedEvent> consumerFactory,
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, BookCatalogChangedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        // 스레드 병렬 처리: 파티션 수에 따라 조정
        factory.setConcurrency(3);

        // 언제 오프셋 커밋할지: BATCH 모드
        factory.getContainerProperties().setAckMode(AckMode.BATCH);

        
        return factory;
    }

//    @Bean
//    public KafkaTemplate<String, BookCategoryChangedEvent> kafkaTemplate() {
//        return new KafkaTemplate<>(producerFactory());
//    }
//
//    @Bean
//    public ProducerFactory<String, BookCategoryChangedEvent> producerFactory() {
//        // 프로듀서 설정은 별도 필요 시 추가
//        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
//    }

}
