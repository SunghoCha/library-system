package msa.bookloan.infra.config.kafka;

import msa.bookloan.adapter.in.messaging.kafka.listener.BookCatalogProjectionEventListener;
import msa.bookloan.adapter.in.messaging.kafka.listener.SagaReplyKafkaListener;
import msa.bookloan.adapter.in.messaging.kafka.util.validator.EventPayloadValidator;
import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxAppender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(KafkaModuleConfig.class)
public class KafkaListenersConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.kafka.listeners", name = "saga-replies.enabled", havingValue = "true")
    public SagaReplyKafkaListener sagaReplyKafkaListener(
            InboxAppender inboxAppender,
            EventPayloadValidator payloadValidator
    ) {
        return new SagaReplyKafkaListener(inboxAppender, payloadValidator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.kafka.listeners", name = "catalog.enabled", havingValue = "true")
    public BookCatalogProjectionEventListener bookCatalogProjectionEventListener(
            InboxAppender inboxAppender,
            EventPayloadValidator payloadValidator
    ) {
        return new BookCatalogProjectionEventListener(inboxAppender, payloadValidator);
    }
}
