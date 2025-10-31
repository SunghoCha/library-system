package msa.common.events.bookloan.saga.reply.member;

@Deprecated
public record MemberCheckedPayload(
        Long memberId,
        boolean blacklisted,
        String reason
) {}
