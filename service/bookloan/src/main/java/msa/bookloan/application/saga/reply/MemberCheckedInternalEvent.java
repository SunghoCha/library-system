package msa.bookloan.application.saga.reply;

import msa.bookloan.application.saga.command.MemberCheckedPayload;

public record MemberCheckedInternalEvent(
        Long eventId,
        String sagaId,
        Long causationEventId,
        Long sourceAggregateVersion,
        MemberCheckedPayload payload
) {}


