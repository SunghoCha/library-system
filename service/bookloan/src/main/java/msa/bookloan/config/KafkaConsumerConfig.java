package msa.bookloan.config;

import lombok.RequiredArgsConstructor;
import msa.common.events.bookcatalog.BookCatalogChangedExternalEventPayload;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;

import static org.springframework.kafka.listener.ContainerProperties.AckMode;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class KafkaConsumerConfig {

    @Bean
    @ConditionalOnBean(KafkaProperties.class)
    public ConsumerFactory<String, BookCatalogChangedExternalEventPayload> consumerFactory(KafkaProperties kafkaProperties) {
        HashMap<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());

        // disable auto-commit
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // JSON 역직렬화 시 신뢰할 패키지
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "msa.common.events");
        // consumer 그룹 아이디
        //props.put(ConsumerConfig.GROUP_ID_CONFIG, "loan-service-bookinfo-replica");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(BookCatalogChangedExternalEventPayload.class, false)
        );
    }

    @Bean(name = "bookCatalogListenerFactory")
    @ConditionalOnBean(ConsumerFactory.class)
    public ConcurrentKafkaListenerContainerFactory<String, BookCatalogChangedExternalEventPayload>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, BookCatalogChangedExternalEventPayload> consumerFactory,
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, BookCatalogChangedExternalEventPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        // 언제 오프셋 커밋할지: RECORD 모드
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        factory.getContainerProperties().setSyncCommits(true);
        factory.setBatchListener(false);

        // 스레드 병렬 처리: 파티션 수에 따라 조정
        factory.setConcurrency(3);

        // TODO : 나중에 카프카 연결하면 설정 풀기
        factory.setAutoStartup(false);

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
