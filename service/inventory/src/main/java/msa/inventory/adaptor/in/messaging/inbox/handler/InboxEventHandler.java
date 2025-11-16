package msa.inventory.adaptor.in.messaging.inbox.handler;

import msa.inventory.adaptor.in.messaging.inbox.InboxMessage;

public interface InboxEventHandler<T> {
    String eventType();                 // 라우팅 키
    Class<T> payloadType();

    void handle(InboxMessage<T> message);
}
