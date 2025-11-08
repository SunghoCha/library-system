package msa.bookloan.adapter.in.messaging.inbox.handler.saga.point;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
import msa.bookloan.application.saga.reply.point.PointChargedInternalEvent;
import msa.common.events.bookloan.saga.reply.SagaReplyType;
import msa.common.events.bookloan.saga.reply.point.PointChargedReply;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointChargedInboxHandler implements InboxEventHandler<PointChargedReply> {

    private final LoanRequestSagaOrchestrator orchestrator;

    @Override
    public String eventType() {
        return SagaReplyType.POINT_CHARGED.getValue();
    }

    @Override
    public Class<PointChargedReply> payloadType() {
        return PointChargedReply.class;
    }

    @Override
    public void handle(InboxMessage<PointChargedReply> message) {
        PointChargedReply payload = message.payload();
        PointChargedInternalEvent internalEvent = PointChargedInternalEvent.from(payload);

        log.info("[SagaHandler][{}] 수신: sagaId={}, eventId={}",
                eventType(), internalEvent.sagaId(), message.eventId());

        orchestrator.onPointCharged(internalEvent);
    }

}
