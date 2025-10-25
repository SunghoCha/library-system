package msa.bookloan.adapter.out.messaging.inbox;

public interface InboxEventHandler<T> {
    String eventType();                 // 라우팅 키
    Class<T> payloadType();

    void handle(Long eventId, T payload);
}
