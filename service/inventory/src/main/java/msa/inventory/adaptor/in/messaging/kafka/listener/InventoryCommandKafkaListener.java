package msa.inventory.adaptor.in.messaging.kafka.listener;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.common.events.MessageEnvelope;
import msa.common.util.EventPayloadValidator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class InventoryCommandKafkaListener {

    private final EventPayloadValidator payloadValidator;

    @KafkaListener
    @Transactional
    public void onReserveInventory(ConsumerRecord<String, MessageEnvelope> record) {
        log.debug("카프카 레코드 수신: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        MessageEnvelope envelope = record.value();

        if (envelope == null) {
            log.warn("[Replies][드롭] envelope=null (topic={}, partition={}, offset={})",
                    record.topic(), record.partition(), record.offset());
            return;
        }


    }

}
