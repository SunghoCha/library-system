package msa.bookloan.application.saga.reply.point;

import msa.common.events.bookloan.saga.reply.point.PointRefundedReply;
import msa.common.util.IdConverter;

public record PointRefundedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long memberId,
        Long refundAmount,
        String refundId
) {

    public static PointRefundedInternalEvent from(PointRefundedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");
        Long memberId = IdConverter.parseLongOrThrow(reply.memberId(), "memberId");

        return new PointRefundedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                memberId,
                reply.refundAmount(),
                reply.refundId()   // 이건 원래 문자열이니까 그대로 둔다
        );
    }
}
