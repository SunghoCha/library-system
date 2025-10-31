package msa.common.events.bookloan.saga.command;


public record CheckMemberCommand(
        Long commandId,
        Long sagaId,
        Long loanId,
        Long memberId,
        Long causationEventId,        // (LoanRequestedInternalEvent.eventId) 이 커맨드를 유발한 직전 이벤트의 id
        String type
) implements SagaCommand {

    public static CheckMemberCommand of(
            Long commandId,
            Long sagaId,
            Long loanId,
            Long memberId,
            Long causationEventId
    ) {
        return new CheckMemberCommand(commandId, sagaId, loanId, memberId, causationEventId,
                SagaCommandType.MEMBER_CHECK.getValue());
    }
}
