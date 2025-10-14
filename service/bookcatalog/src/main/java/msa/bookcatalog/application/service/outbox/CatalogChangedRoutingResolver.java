package msa.bookcatalog.application.service.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.application.event.BookCatalogChangedEvent;
import msa.bookcatalog.infra.config.properties.KafkaProps;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogChangedRoutingResolver implements OutboxRoutingResolver<BookCatalogChangedEvent> {

    private final KafkaProps kafkaProps;

    @Override
    public Class<BookCatalogChangedEvent> payloadType() {
        return BookCatalogChangedEvent.class;
    }

    @Override
    public OutboxRouting doResolve(BookCatalogChangedEvent event) {
        return OutboxRouting.builder()
                .topic(kafkaProps.getTopicCatalogChanged())
                .partitionKey(String.valueOf(event.getAggregateId()))
                .build();
    }
}
