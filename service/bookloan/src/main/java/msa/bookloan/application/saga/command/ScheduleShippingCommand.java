package msa.bookloan.application.saga.command;


public record ScheduleShippingCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long bookId,
        Long causationEventId,        // 직전 내부 이벤트의 eventId (PointChargedInternalEvent.eventId)
        String type
) implements SagaCommand {

    public static ScheduleShippingCommand of(
            Long cmdId,
            String sagaId,
            Long loanId,
            Long bookId,
            Long causationId) {
        return new ScheduleShippingCommand(cmdId, sagaId, loanId, bookId, causationId,
                CommandTypes.SHIPPING_SCHEDULE);
    }
}
