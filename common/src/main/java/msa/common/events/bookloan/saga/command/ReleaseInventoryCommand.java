package msa.common.events.bookloan.saga.command;


public record ReleaseInventoryCommand(
        Long commandId,
        Long sagaId,
        Long loanId,
        Long bookId,
        Long causationEventId, // 직전 내부 이벤트 ID
        String type
) implements SagaCommand {

    public static ReleaseInventoryCommand of(
            Long commandId,
            Long sagaId,
            Long loanId,
            Long bookId,
            Long causationEventId
    ) {
        return new ReleaseInventoryCommand(commandId, sagaId, loanId, bookId, causationEventId,
                SagaCommandType.INVENTORY_RELEASE.getValue());
    }
}
