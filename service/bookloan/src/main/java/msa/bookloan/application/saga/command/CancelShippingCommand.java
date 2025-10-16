package msa.bookloan.application.saga.command;

import lombok.Builder;

@Builder
public record CancelShippingCommand(
        Long commandId, String sagaId, Long loanId, Long bookId,
        Long sourceAggregateVersion, Long causationEventId
){ }