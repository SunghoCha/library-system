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
        Long paymentId
) {

    public static PointChargedInternalEvent from(PointChargedReply reply) {
        Long eventId = IdConverter.parseLongOrThrow(reply.eventId(), "eventId");
        Long sagaId = IdConverter.parseLongOrThrow(reply.sagaId(), "sagaId");
        Long causationCommandId = IdConverter.parseLongOrThrow(reply.causationCommandId(), "causationCommandId");
        Long memberId = IdConverter.parseLongOrThrow(reply.memberId(), "memberId");
        Long paymentId = IdConverter.parseLongOrThrow(reply.paymentId(), "paymentId");

        return new PointChargedInternalEvent(
                eventId,
                sagaId,
                causationCommandId,
                reply.loanVersion(),
                memberId,
                reply.amount(),
                paymentId
        );
    }
}
