package msa.bookloan.application.saga.reply.point;

public record PointRefundedPayload(
        Long memberId,
        Long refundAmount,     // 환불된 포인트 양
        Long refundId          // 멤버 서비스 내부 환불 트랜잭션 ID
) {}
