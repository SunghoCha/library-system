package msa.bookloan.application.saga.command;

public record MemberCheckedPayload(
        Long memberId,
        boolean blacklisted,
        String reason
) {}
