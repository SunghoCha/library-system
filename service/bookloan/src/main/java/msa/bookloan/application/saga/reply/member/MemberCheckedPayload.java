package msa.bookloan.application.saga.reply.member;

public record MemberCheckedPayload(
        Long memberId,
        boolean blacklisted,
        String reason
) {}
