package msa.common.events.bookloan.saga.reply.point;

@Deprecated
public record PointChargedPayload(
        Long memberId,
        Long amount,      // 사용 포인트
        Long paymentId    // 결제 트랜잭션 id
) {}