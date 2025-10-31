package msa.common.events;

public interface EnvelopeMeta {
    String eventId();
    String aggregateId();
    Long   aggregateVersion(); // 도메인: 필수, 사가: null 허용
    String eventType();
}
