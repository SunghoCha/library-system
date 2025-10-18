package msa.bookloan.application.saga.command;


public record ConfirmShippingCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long bookId,
        Long causationEventId,
        String type
) implements SagaCommand {

    public static ConfirmShippingCommand of(
            Long cmdId,
            String sagaId,
            Long loanId,
            Long bookId,
            Long causationId
    ) {
        return new ConfirmShippingCommand(cmdId, sagaId, loanId, bookId, causationId,
                CommandTypes.SHIPPING_CONFIRM);
    }
}