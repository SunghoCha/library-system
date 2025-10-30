package msa.common.events.bookloan.saga.command;


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
                SagaCommandType.SHIPPING_CONFIRM.getValue());
    }
}