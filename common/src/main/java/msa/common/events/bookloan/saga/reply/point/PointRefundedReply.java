package msa.common.events.bookloan.saga.reply.point;

import msa.common.events.bookloan.saga.reply.SagaReplyEvent;

public record PointRefundedReply(
        String eventId,
        String sagaId,
        String causationCommandId,
        Long loanVersion,
        String memberId,
        Long refundAmount,     // 환불된 포인트 양
        String refundId          // 멤버 서비스 내부 환불 트랜잭션 ID
) implements SagaReplyEvent {}
