package msa.bookloan.application.event;


import java.time.LocalDateTime;

public record LoanRequestedInternalEvent(
        String sagaId,
        Long loanId,
        Long memberId,
        Long bookId,
        Long eventId,
        Long aggregateVersion,
        LocalDateTime occurredAt
        ) {
}
