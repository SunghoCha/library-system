package msa.bookcatalog.infra.outbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private final EventRecorder eventRecorder;

    @Transactional
    public void updateStatusAfterProcessing(Long eventId, Throwable ex) {
        try {
            if (ex == null) {
                eventRecorder.markAsPublished(eventId);
            } else {
                eventRecorder.handleFailure(eventId, ex.getMessage());
            }
        } catch (Exception  e) {
            log.error("Failed to update outbox status for eventId={} after processing. Manual check required.", eventId, e);
        }
    }

}
