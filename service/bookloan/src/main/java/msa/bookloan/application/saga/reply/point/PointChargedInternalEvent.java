package msa.bookloan.application.saga.reply.point;

import msa.common.events.bookloan.saga.reply.point.PointChargedReply;
import msa.common.util.IdConverter;

public record PointChargedInternalEvent(
        Long eventId,
        Long sagaId,
        Long causationCommandId,
        Long loanVersion,
        Long memberId,
        Long amount,
        String paymentId
) {

    public static PointChargedInternalEvent from(PointChargedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");
        Long memberId = IdConverter.parseLongOrThrow(reply.memberId(), "memberId");

        return new PointChargedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                memberId,
                reply.amount(),
                reply.paymentId()   // 이건 원래 String으로 유지
        );
    }
}
