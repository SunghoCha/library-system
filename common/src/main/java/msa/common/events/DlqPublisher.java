package msa.common.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;

@Component
public class DlqPublisher {

    private final DeadLetterPublishingRecoverer recoverer;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DlqPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
                ((consumerRecord, exception) ->
                        new TopicPartition(consumerRecord.topic() + ".DLQ", consumerRecord.partition()))
        );
    }

    /**
     * ConsumerRecord 객체 전체를 DLQ로 보낼 때 사용
     *
     * @param record ConsumerRecord<K,V> (원본 메시지)
     * @param cause  실패 원인이 되는 예외
     * @param <K>    키 타입
     * @param <V>    값 타입
     */
    public <K, V> void publish(ConsumerRecord<K, V> record, Exception cause) {
        // DeadLetterPublishingRecoverer가 자동으로 원본 토픽명 + ".DLQ" 토픽으로 분기
        recoverer.accept(record, cause);
    }
}
