package msa.common.events.bookloan.saga.command;


public record ReserveInventoryCommand(
        Long commandId,
        Long sagaId,
        Long loanId,
        Long bookId,
        Long causationEventId,   // 커맨드 추적용 id (MemberCheck의 commandId)
        String type
) implements SagaCommand {

    public static ReserveInventoryCommand of(
            Long cmdId,
            Long sagaId,
            Long loanId,
            Long bookId,
            Long causationId
    ) {
        return new ReserveInventoryCommand(cmdId, sagaId, loanId, bookId, causationId,
                SagaCommandType.INVENTORY_RESERVE.getValue());
    }
}
