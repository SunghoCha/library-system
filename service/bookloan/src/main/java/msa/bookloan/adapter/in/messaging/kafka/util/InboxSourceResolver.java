package msa.bookloan.adapter.in.messaging.kafka.util;

import msa.common.domain.model.InboxSource;
import org.springframework.stereotype.Component;

@Component
public class InboxSourceResolver {

    /**
     * 예) "book-catalog.changed" → BOOK_CATALOG
     *     "dev.book-catalog.changed" → BOOK_CATALOG
     *     "payment.completed" → PAYMENT
     */
    public InboxSource resolveFromTopic(String topic) {
        String[] tokens = topic.split("\\.");
        for (String token : tokens) {
            try {
                return InboxSource.fromValue(token); // enum value("book-catalog" 등)과 매칭
            } catch (IllegalArgumentException ignore) {
                // 다음 토큰 시도
            }
        }
        throw new IllegalArgumentException("No matching InboxSource in topic: " + topic);
    }
}
