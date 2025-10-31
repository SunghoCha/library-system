package msa.bookloan.adapter.out.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.persistence.outbox.entity.OutboxEventRecord;
import msa.bookloan.adapter.out.persistence.outbox.repository.OutboxEventRecordRepository;
import msa.bookloan.application.event.CatalogEventType;
import msa.common.events.outbox.OutboxRecordableEvent;
import msa.common.events.outbox.OutboxRoutingResolver;
import msa.common.events.outbox.dto.OutboxRouting;
import msa.common.snowflake.Snowflake;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static java.time.LocalDateTime.*;
import static msa.common.events.outbox.OutboxEventRecordStatus.NEW;
// TODO : 사가 시작을 로컬트랜잭션에서 수행하는걸로 바뀌어서 아직은 필요없는 클래스인 상태. 추후 수정
@Slf4j
@Component
@RequiredArgsConstructor
public class EventRecorder {
    private static final String AGGREGATE_TYPE = "BookLoan";

    private final Clock clock;
    private final Snowflake snowflake;
    private final ObjectMapper objectMapper;
    private final OutboxEventRecordRepository eventRecordRepository;
    private final List<OutboxRoutingResolver<?>> resolvers;

    @Deprecated // TODO: 트랜잭션걸고 save에서 발생하는 예외가 catch에서 잡히지도 않을거고 추후 필요할떄 수정해야함. 업서트로 할 듯?
    @Transactional
    public void save(OutboxRecordableEvent event) {
        OutboxEventRecord record = toRecord(event);
        try {
            eventRecordRepository.save(record);
            log.debug("[Outbox] saved: eventId={}, dbId={}", event.eventId(), record.getId());
        } catch (DataIntegrityViolationException e) {
            // eventId 유니크 충돌 : 이미 저장된 이벤트로 간주하고 스킵
            log.info("[Outbox] duplicate skipped: eventId={}", event.eventId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markPublishedByEventId(Long eventId, String leaseId) {
        long updated = eventRecordRepository.markPublishedByEventId(eventId, leaseId, now(clock));
        if (updated == 0L) {
            log.info("[Outbox] 발행 처리 스킵(펜싱/이미 처리): eventId={}, leaseId={}",
                    eventId, leaseId);
        } else {
            log.info("[Outbox] 발행 완료: eventId={}", eventId);
        }
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markFailedByEventId(Long eventId, String leaseId, String reason) {
        long updated = eventRecordRepository.markFailedByEventId(eventId, leaseId, reason, now(clock));
        if (updated == 0L) {
            log.info("[Outbox] 실패 처리 스킵(펜싱/회수됨): eventId={}, leaseId={}",
                    eventId, leaseId);
        } else {
            log.warn("[Outbox] 발행 실패: eventId={}, reason={}", eventId, reason);
        }
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long markDeadLetter(Long eventId, String error) {
        long updated = eventRecordRepository.markDeadFromFailed(eventId, error, now(clock));
        if (updated == 0L) {
            log.info("[Outbox] 데드레터 전이 스킵(FAILED 아님): eventId={}", eventId);
        } else {
            log.error("[Outbox] 데드레터 전이: eventId={}, error={}", eventId, error);
        }
        return updated;
    }



    private OutboxEventRecord toRecord(OutboxRecordableEvent event) {
        String payload = serializeToPayload(event);

        OutboxRouting routing = route(event);
        if (routing == null || routing.getTopic() == null || routing.getPartitionKey() == null) {
            throw new IllegalStateException("Invalid routing for event: " + event);
        }

        return OutboxEventRecord.builder()
                .id(snowflake.nextId())
                .eventId(event.eventId())
                .eventType(CatalogEventType.CREATED.getValue())                 // 사가 시작이므로 CREATED로 고정
                //.aggregateId(String.valueOf(event.loanId())) // TODO : 체크해야함
                .aggregateType(AGGREGATE_TYPE)
                .aggregateVersion(event.aggregateVersion())
                .payload(payload)
                .occurredAt(event.occurredAt())
                .outboxEventRecordStatus(NEW)
                .routing(routing)
                .build();
    }

    private OutboxRouting route(OutboxRecordableEvent event) {
        OutboxRoutingResolver<?> target = null;

        for (OutboxRoutingResolver<?> resolver : resolvers) {
            if (resolver.supports(event)) {
                target = resolver;
            }
        }

        if (target == null) {
            throw new IllegalStateException("No OutboxRoutingResolver for type: " + event.getClass().getName());
        }

        OutboxRouting routing = target.resolve(event);
        if (routing == null || routing.getTopic() == null) {
            throw new IllegalStateException("Resolver returned null routing/topic for " + event.getClass().getName());
        }

        return routing;
    }

    private String serializeToPayload(OutboxRecordableEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload serialize failed: eventId=" + event.eventId(), e);
        }
    }
}
