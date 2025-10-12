package msa.bookloan.application.saga.reply.member;

public record MemberCheckedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationCommandId,
        Long sourceAggregateVersion,
        MemberCheckedPayload payload
) {}


