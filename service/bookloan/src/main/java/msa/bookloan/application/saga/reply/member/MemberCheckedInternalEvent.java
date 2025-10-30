package msa.bookloan.application.saga.reply.member;

import msa.common.events.bookloan.saga.reply.member.MemberCheckedReply;
import msa.common.util.IdConverter;

public record MemberCheckedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long memberId,
        boolean blacklisted,
        String reason
) {

    public static MemberCheckedInternalEvent from(MemberCheckedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");
        Long memberId = IdConverter.parseLongOrThrow(reply.memberId(), "memberId");

        return new MemberCheckedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),   // 이건 원래 Long 이니까 그대로
                memberId,
                reply.blacklisted(),
                reply.reason()
        );
    }
}
