package msa.bookloan.application.saga.command;

import lombok.Builder;

@Builder
public record CheckMemberCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long memberId,
        Long sourceAggregateVersion, // BookLoan.@Version
        Long causationEventId        // (LoanRequestedInternalEvent.eventId) 이 커맨드를 유발한 직전 이벤트의 id
) {}
