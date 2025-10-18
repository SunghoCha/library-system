package msa.bookloan.application.saga.command;


public record CancelShippingCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long bookId,
        Long causationEventId,
        String type
) implements SagaCommand {

    public static CancelShippingCommand of(
            Long cmdId,
            String sagaId,
            Long loanId,
            Long bookId,
            Long causationId
    ) {
        return new CancelShippingCommand(cmdId, sagaId, loanId, bookId, causationId,
                CommandTypes.SHIPPING_CANCEL);
    }
}