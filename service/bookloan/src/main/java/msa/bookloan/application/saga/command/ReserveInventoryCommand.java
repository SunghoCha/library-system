package msa.bookloan.application.saga.command;


public record ReserveInventoryCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long bookId,
        Long causationEventId,   // 커맨드 추적용 id (MemberCheck의 commandId)
        String type
) implements SagaCommand {

    public static ReserveInventoryCommand of(
            Long cmdId,
            String sagaId,
            Long loanId,
            Long bookId,
            Long causationId
    ) {
        return new ReserveInventoryCommand(cmdId, sagaId, loanId, bookId, causationId,
                SagaCommandType.INVENTORY_RESERVE.getValue());
    }
}
