package msa.bookloan.adapter.in.messaging.inbox.handler;

import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;

public interface InboxEventHandler<T> {
    String eventType();                 // 라우팅 키
    Class<T> payloadType();

    void handle(InboxMessage<T> message);
}
