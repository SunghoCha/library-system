package msa.bookcatalog.infra.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.bookcatalog.service.exception.OutboxEventRecordNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private final EventRecorder eventRecorder;

    @Transactional
    public void updateRecord(Long eventId, Throwable ex) {
        try {
            if (ex == null) {
                eventRecorder.markAsPublished(eventId);
            } else {
                eventRecorder.markAsFailed(eventId);
            }
        } catch (OutboxEventRecordNotFoundException notFound) {
            log.warn("Outbox record not found (ignoring): eventId={}", eventId, notFound);
        } catch (Exception e) {
            markAsDeadLetter(eventId);
        }
    }

    private void markAsDeadLetter(Long eventId) {
        try {
            eventRecorder.markAsDeadLetter(eventId);
        } catch (Exception dlEx) {
            log.warn("DEAD_LETTER marking failed, eventId={}", eventId, dlEx);
        }
    }

    public void recordSuccess(Long eventId) {
        try {
            eventRecorder.markAsPublished(eventId);
        } catch (OutboxEventRecordNotFoundException notFound) {
            log.warn("Outbox record not found (ignoring): eventId={}", eventId, notFound);
        } catch (Exception e) {
            markAsDeadLetter(eventId);
        }
    }
}
