package msa.bookloan.application.outbox;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.event.LoanRequestedInternalEvent;
import msa.bookloan.infra.config.properties.KafkaProps;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import org.springframework.stereotype.Component;

//@Component
//@RequiredArgsConstructor
//public class LoanOutboxRoutingResolver implements OutboxRoutingResolver<LoanRequestedInternalEvent> {
//
//    private final KafkaProps kafkaProps;
//
//    @Override
//    public Class<LoanRequestedInternalEvent> payloadType() {
//        return LoanRequestedInternalEvent.class;
//    }
//
//    @Override
//    public OutboxRouting resolve(LoanRequestedInternalEvent event) {
//        return OutboxRouting.builder()
//                .topic(kafkaProps.getGroupLoanRequested())
//                .partitionKey(event.sagaId())
//                .build();
//    }
//}
