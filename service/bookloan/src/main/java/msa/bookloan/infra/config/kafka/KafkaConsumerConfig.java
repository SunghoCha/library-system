package msa.bookloan.infra.config.kafka;

import msa.common.events.MessageEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.kafka.listener.ContainerProperties.AckMode;

@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, MessageEnvelope> consumerFactory(
            KafkaProperties kafkaProperties,
            ObjectProvider<SslBundles> sslBundlesProvider
    ) {
        SslBundles sslBundles = sslBundlesProvider.getIfAvailable(); // 없으면 null 들어가도 됨
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(sslBundles));

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(MessageEnvelope.class, false)
        );
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, MessageEnvelope>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, MessageEnvelope> consumerFactory,
            DefaultErrorHandler errorHandler,
            KafkaProperties kafkaProperties) {

        ConcurrentKafkaListenerContainerFactory<String, MessageEnvelope> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        // 언제 오프셋 커밋할지: RECORD 모드. 나중에 다른 모드도 사용해야하면 외부변수화
        AckMode ackMode = kafkaProperties.getListener().getAckMode();
        factory.getContainerProperties().setAckMode(ackMode != null ? ackMode : AckMode.RECORD);

        factory.getContainerProperties().setSyncCommits(true);
        factory.setBatchListener(false);

        // 스레드 병렬 처리: 파티션 수에 따라 조정
        Integer concurrency = kafkaProperties.getListener().getConcurrency();
        if (concurrency != null) {
            factory.setConcurrency(concurrency);
        }

        // TODO : 나중에 카프카 연결하면 설정 풀기
        factory.setAutoStartup(kafkaProperties.getListener().isAutoStartup());

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
