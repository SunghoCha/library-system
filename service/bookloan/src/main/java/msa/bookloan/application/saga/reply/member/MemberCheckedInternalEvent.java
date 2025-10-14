package msa.bookloan.application.saga.reply.member;

import msa.bookloan.application.saga.reply.SagaReplyEvent;

public record MemberCheckedInternalEvent(
        Long eventId, // 리플라이의 eventId
        String sagaId,
        Long causationCommandId, // 리플라이를 트리거한 커맨드의 id
        Long sourceAggregateVersion, // LoanId
        MemberCheckedPayload payload
) implements SagaReplyEvent {}


