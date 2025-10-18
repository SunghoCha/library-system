package msa.bookloan.application.saga.command;


public record RefundPointCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long memberId,
        Long causationEventId,        // 직전 내부 이벤트 ID
        String type
) implements SagaCommand {

    public static RefundPointCommand of(
            Long commandId,
            String sagaId,
            Long loanId,
            Long memberId,
            Long causationEventId
    ) {
        return new RefundPointCommand(commandId, sagaId, loanId, memberId, causationEventId,
                CommandTypes.POINT_REFUND);
    }
}