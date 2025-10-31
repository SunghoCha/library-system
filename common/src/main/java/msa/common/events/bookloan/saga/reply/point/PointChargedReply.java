package msa.common.events.bookloan.saga.reply.point;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record PointChargedReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String memberId,
        Long amount,      // 사용 포인트
        String paymentId    // 결제 트랜잭션 id

) implements SagaReplyEvent {}
