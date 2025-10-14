package msa.bookloan.application.saga.reply.point;

public record PointChargedPayload(
        Long memberId,
        Long amount,      // 사용 포인트
        Long paymentId    // 결제 트랜잭션 id
) {}