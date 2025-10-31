package msa.common.events.bookloan.saga.command;


public record CancelShippingCommand(
        Long commandId,
        Long sagaId,
        Long loanId,
        Long bookId,
        Long causationEventId,
        String type
) implements SagaCommand {

    public static CancelShippingCommand of(
            Long cmdId,
            Long sagaId,
            Long loanId,
            Long bookId,
            Long causationId
    ) {
        return new CancelShippingCommand(cmdId, sagaId, loanId, bookId, causationId,
                SagaCommandType.SHIPPING_CANCEL.getValue());
    }
}