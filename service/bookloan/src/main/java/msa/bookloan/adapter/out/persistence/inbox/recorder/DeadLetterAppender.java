package msa.bookloan.adapter.out.persistence.inbox.recorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxDeadLetterRepository;
import msa.common.domain.model.InboxSource;
import msa.common.events.inbox.record.InboxDeadLetter;
import msa.common.exception.FailureCategory;
import msa.common.snowflake.Snowflake;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeadLetterAppender {

    private final InboxDeadLetterRepository repository;
    private final ObjectMapper objectMapper;
    private final Snowflake snowflake;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> void save(
            ConsumerRecord<String, T> record,
            InboxSource source,
            FailureCategory category,
            T payload,
            String errorMessage
    ) {
        String json = serializeOrNull(payload);
        String msg = StringUtils.abbreviate(errorMessage, 2000);

        InboxDeadLetter row = InboxDeadLetter.builder()
                .id(snowflake.nextId())
                .source(source.name())
                .topic(record.topic())
                .partitionNo(record.partition())
                .recordOffset(record.offset())
                .payloadJson(json)
                .errorMessage(msg)
                .errorCategory(category.name())
                .build();

        repository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> void save(ConsumerRecord<String, T> record,
                         InboxSource source,
                         FailureCategory category,
                         String errorMessage) {
        // payload는 record.value() 그대로
        save(record, source, category, record.value(), errorMessage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> void save(ConsumerRecord<String, T> record,
                         InboxSource source,
                         FailureCategory category) {
        // errorMessage 없이도 호출 가능(기존 호출부 호환)
        save(record, source, category, record.value(), null);
    }

    private String serializeOrNull(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignore) {
            return null;
        }
    }
}
