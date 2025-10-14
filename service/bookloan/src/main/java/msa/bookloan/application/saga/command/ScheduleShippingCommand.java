package msa.bookloan.application.saga.command;

import lombok.Builder;

@Builder
public record ScheduleShippingCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long bookId,
        Long sourceAggregateVersion, // BookLoan.@Version (출처 버전)
        Long causationEventId        // 직전 내부 이벤트의 eventId (PointChargedInternalEvent.eventId)
) implements SagaCommand { }
