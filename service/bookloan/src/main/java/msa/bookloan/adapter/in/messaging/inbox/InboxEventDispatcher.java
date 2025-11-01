package msa.bookloan.adapter.in.messaging.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
import msa.bookloan.adapter.in.messaging.kafka.util.validator.EventPayloadValidator;
import msa.bookloan.adapter.out.persistence.inbox.recorder.InboxStatusMarker;
import msa.bookloan.adapter.out.persistence.inbox.entity.InboxEventRecord;
import msa.bookloan.adapter.out.persistence.inbox.repository.InboxEventRecordRepository;
import msa.common.config.properties.InboxProcessingProps;
import msa.common.exception.BusinessNotRetryableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.apache.commons.lang3.StringUtils.abbreviate;

@Slf4j
@Service
public class InboxEventDispatcher {

    private final ObjectMapper objectMapper;
    private final EventPayloadValidator validator;
    private final InboxStatusMarker inboxStatusMarker;
    private final InboxProcessingProps inboxProcessingProps;
    private final InboxEventRecordRepository recordRepository;
    private final Map<String, InboxEventHandler<?>> handlerMap;

    public InboxEventDispatcher(
            ObjectMapper objectMapper,
            EventPayloadValidator validator,
            InboxStatusMarker inboxStatusMarker,
            InboxProcessingProps inboxProcessingProps,
            InboxEventRecordRepository recordRepository,
            List<InboxEventHandler<?>> handlers
    ) {

        HashMap<String, InboxEventHandler<?>> map = new HashMap<>();
        for (InboxEventHandler<?> handler : handlers) {
            String eventType = handler.eventType();
            InboxEventHandler<?> previousHandler = map.put(eventType, handler);
            if (previousHandler != null) {
                throw new IllegalStateException("Duplicate handler for eventType=" + eventType);
            }
        }
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.inboxStatusMarker = inboxStatusMarker;
        this.inboxProcessingProps = inboxProcessingProps;
        this.recordRepository = recordRepository;
        this.handlerMap = Collections.unmodifiableMap(map);
    }

    @Transactional
    public void processEvent(Long eventId, String leaseId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(leaseId, "leaseId must not be null");

        int maxLength = inboxProcessingProps.errorMaxLength();

        InboxEventRecord record = recordRepository.findByEventId(eventId).orElse(null);
        if (record == null) {
            log.info("[Inbox] 선점 무효(레코드 없음): eventId={} leaseId={}", eventId, leaseId);
            return;
        }

        String eventType = record.getEventType();
        InboxEventHandler<?> eventHandler = handlerMap.get(eventType);

        if (eventHandler == null) { // eventType 문제인지 handler 문제인지 구별
            String errType = (eventType == null || eventType.isBlank()) ? "NO_EVENT_TYPE" : "NO_HANDLER";
            String reason = abbreviate(errType + ": " + eventType, maxLength);
            inboxStatusMarker.markDeadLetter(eventId, leaseId, reason);
            log.warn("[Inbox] {} DLT로 전송 (eventId={}, eventType={}, leaseId={})",
                    reason, eventId, eventType, leaseId);
            return;
        }

        try {
            Object payload = objectMapper.readValue(record.getPayload(), eventHandler.payloadType());
            validator.validateOrThrow(payload); // payload 구체애 대한 검증이 컨슈머에서 수행되지 않아서 여기서 처리
            dispatch(eventHandler, record, payload);
            inboxStatusMarker.markProcessed(eventId, leaseId);

        } catch (ConstraintViolationException cvEx) {
            String reason = abbreviate(cvEx.getMessage(), maxLength);
            inboxStatusMarker.markDeadLetter(eventId, leaseId, reason);
            log.warn("[Inbox] 유효성 오류 DLT: eventId={}, type={}, reason={}", eventId, eventType, reason);

        } catch (BusinessNotRetryableException bizEx) {
            inboxStatusMarker.markDeadLetter(eventId, leaseId, bizEx.getMessage()); // REQUIRES_NEW 커밋
            log.warn("[Inbox] 비재시도 오류 DLT 전송: eventId={}, type={}, err={}", eventId, eventType, bizEx.toString());

            throw bizEx;

        } catch (JsonProcessingException jsonEx) {
            String reason = abbreviate("DESERIALIZATION_ERROR: " + jsonEx.getOriginalMessage(), maxLength);
            inboxStatusMarker.markDeadLetter(eventId, leaseId, reason);
            log.warn("[Inbox] JSON 파싱 실패. eventId={}", eventId, jsonEx);

            throw new RuntimeException("JSON parsing failed, rolling back", jsonEx);

        } catch (OptimisticLockingFailureException olfEx) { // 처리권 상실이므로 마킹하면 안될듯
            log.info("[Inbox] 경합으로 처리권 상실, 롤백: eventId={}, leaseId={}", eventId, leaseId);
            throw olfEx;

        } catch (Exception ex) {
            String reason = abbreviate(ex.getClass().getSimpleName() + ": " +
                    Objects.toString(ex.getMessage(), ex.toString()), maxLength);
            inboxStatusMarker.markFailed(eventId, leaseId, reason);
            log.warn("[Inbox] 처리 실패(재시도 예정) (eventId={}, type={}, err={})",
                    eventId, eventType, ex.toString());

            throw ex;
        }

    }

    private <T> void dispatch(InboxEventHandler<T> eventHandler, InboxEventRecord record, Object payloadObj) {
        Class<T> payloadType = eventHandler.payloadType();

        T payload = payloadType.cast(payloadObj);
        if (payload == null) {
            throw new BusinessNotRetryableException(
                    "Payload is null: expected=" + payloadType.getName() + ", eventId=" + record.getEventId());
        }

        InboxMessage<T> message = new InboxMessage<>(
                payload,
                record.getEventId(),
                record.getAggregateId(),
                record.getAggregateVersion(), // 프로젝션 핸들러를 위해 버전 전달, 사가의 경우 null
                record.getEventType()
        );

        eventHandler.handle(message);

    }
}
