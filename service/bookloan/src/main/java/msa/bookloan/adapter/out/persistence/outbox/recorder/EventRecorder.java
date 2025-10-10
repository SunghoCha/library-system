package msa.bookloan.adapter.out.persistence.outbox.recorder;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.outbox.LoanOutboxRoutingResolver;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventRecorder {

    private final LoanOutboxRoutingResolver routingResolver;
}
