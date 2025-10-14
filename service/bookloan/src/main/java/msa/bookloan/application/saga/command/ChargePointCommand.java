package msa.bookloan.application.saga.command;

import lombok.Builder;

@Builder
public record ChargePointCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long memberId,
        Long sourceAggregateVersion, // BookLoan 버전
        Long causationEventId        // 직전 내부 이벤트의 eventId (InventoryReservedInternalEvent.eventId)
) implements SagaCommand { }
