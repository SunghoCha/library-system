package msa.common.events.bookloan.saga.command;


public record ChargePointCommand(
        Long commandId,
        Long sagaId,
        Long loanId,
        Long memberId,
        Long causationEventId,        // 직전 내부 이벤트의 eventId (InventoryReservedInternalEvent.eventId)
        String type
) implements SagaCommand {

    public static ChargePointCommand of(
            Long cmdId,
            Long sagaId,
            Long loanId,
            Long memberId,
            Long causationId
    ) {
        return new ChargePointCommand(cmdId, sagaId, loanId, memberId, causationId,
                SagaCommandType.POINT_CHARGE.getValue());
    }
}
