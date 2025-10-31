package msa.bookcatalog.adapter.out.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookcatalog.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookcatalog.application.event.BookCatalogChangedEvent;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.bookcatalog.BookCatalogChangedPayload;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.*;
import static msa.common.events.outbox.OutboxEventRecordStatus.NEW;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventRecorder {

    private final Clock clock;
    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final OutboxEventRecordRepository eventRecordRepository;
    private final OutboxRoutingResolver<BookCatalogChangedEvent> routingResolver;

    // TODO : 추후 이벤트 종류 늘어나면 제네릭 메서드로 변경 예정
    @Transactional
    public boolean save(BookCatalogChangedEvent event) {
        String payloadJson = serializeToPayload(event);
        OutboxRouting routing = routingResolver.doResolve(event);
        if (routing == null || routing.getTopic() == null || routing.getPartitionKey() == null) {
            throw new IllegalStateException("Routing is invalid. eventId={} " + event.getEventId());
        }

        int affected = eventRecordRepository.upsertOutbox(
                snowflake.nextId(),
                event.getEventId(),
                event.getEventType(),
                String.valueOf(event.getAggregateId()),
                event.getAggregateType(),
                event.getAggregateVersion(),
                payloadJson,
                routing.getTopic(),
                routing.getPartitionKey(),
                event.getOccurredAt());

        boolean isNew = (affected == 1);

        if (isNew) {
            log.debug("[Outbox] inserted: type={} aggType={} aggId={} eventId={} topic={}",
                    event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                    event.getEventId(), routing.getTopic());
        } else {
            log.debug("[Outbox] duplicate-skip: type={} aggType={} aggId={} eventId={}",
                    event.getEventType(), event.getAggregateType(), event.getAggregateId(), event.getEventId());
        }

        return isNew;
    }

    @Transactional
    public void saveAll(List<BookCatalogChangedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        Set<Long> eventIdsToSave = events.stream()
                .map(BookCatalogChangedEvent::getEventId)
                .collect(Collectors.toSet());

        Set<Long> existingEventIds = eventRecordRepository.findExistingEventIdsByEventIdIn(eventIdsToSave);

        List<OutboxEventRecord> newRecords = events.stream()
                .filter(event -> !existingEventIds.contains(event.getEventId()))
                .collect(Collectors.toSet()) // eventId로 이퀄스해시코드 구현해서 중복 제거
                .stream()
                .map(this::toRecord)
                .toList();

        if (newRecords.isEmpty()) {
            log.info("[Outbox] 저장할 새로운 이벤트가 없습니다. (전체 {}건 중 중복 {}건)", events.size(), existingEventIds.size());
            return;
        }

        eventRecordRepository.saveAll(newRecords); // 현재 단일 리더 스케줄러 저장방식이라 동시성 문제는 없을듯
        log.debug("OutboxEventRecord {}건 저장 완료. (중복 {}건 스킵)", newRecords.size(), existingEventIds.size());
    }

    public OutboxEventRecord toRecord(BookCatalogChangedEvent event) {
        String payload = serializeToPayload(event);

        OutboxRouting routing = routingResolver.doResolve(event);
        if (routing == null || routing.getTopic() == null || routing.getPartitionKey() == null) {
            throw new IllegalStateException("Routing is invalid: " + event);
        }

        return OutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .aggregateId(event.getAggregateId())
                .aggregateType(event.getAggregateType())
                .aggregateVersion(event.getAggregateVersion())
                .payload(payload)
                .occurredAt(event.getOccurredAt())
                .outboxEventRecordStatus(NEW)
                .routing(routing)
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markPublishedByEventId(Long eventId, String leaseId) {
        long updated = eventRecordRepository.markPublishedByEventId(eventId, leaseId, now(clock));

        if (updated == 0) {
            log.info("[Outbox] 발행 처리 스킵(펜싱/이미 처리): eventId={}, leaseId={}",
                    eventId, leaseId);
        } else {
            log.info("[Outbox] 발행 완료 (eventId={})", eventId);
        }
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markFailedByEventId(Long eventId, String leaseId, String reason) {
        long updated = eventRecordRepository.markFailedByEventId(eventId, leaseId, reason, now(clock));

        if (updated == 0) {
            log.info("[Outbox] 실패 처리 스킵(펜싱/회수됨): eventId={}, leaseId={}",
                    eventId, leaseId);
        } else {
            log.warn("[Outbox] 발행 실패 (eventId={}, 이유={})", eventId, reason);
        }
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markDeadLetter(Long eventId, String error) {
        long updated = eventRecordRepository.markDeadFromFailed(eventId, error, now(clock));

        if (updated == 0) {
            log.info("[Outbox] 데드레터 전이 스킵: 현재 상태가 FAILED 아님 (eventId={})", eventId);
        } else {
            log.error("[Outbox] 데드레터로 전이 (eventId={}, 이유={})", eventId, error);
        }

        return updated;
    }

    private String serializeToPayload(BookCatalogChangedEvent event) {
        try {
            BookCatalogChangedPayload payload = new BookCatalogChangedPayload(
                    String.valueOf(event.getEventId()),
                    event.getEventType(),
                    String.valueOf(event.getBookId()),
                    event.getAggregateVersion(),
                    String.valueOf(event.getAggregateId()),
                    event.getAggregateType(),
                    event.getTitle(),
                    event.getAuthor(),
                    new CategoryRef(event.getCategory().categoryId(), event.getCategory().categoryName()),
                    new BookTypeRef(event.getBookType().bookType(), event.getBookType().bookTypeName()),
                    event.getOccurredAt()
            );
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialize failed: eventId=" + event.getEventId(), e);
        }
    }

}
