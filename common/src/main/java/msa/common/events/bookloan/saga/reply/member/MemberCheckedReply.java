package msa.common.events.bookloan.saga.reply.member;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record MemberCheckedReply(
        String eventId, // 리플라이의 eventId
        String sagaId,
        String causationCommandId, // 리플라이를 트리거한 커맨드의 id
        Long loanVersion, // loanId
        String memberId,
        boolean blacklisted,
        String reason
) implements SagaReplyEvent {}


