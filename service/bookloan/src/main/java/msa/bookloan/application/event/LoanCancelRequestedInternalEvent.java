package msa.bookloan.application.event;

import msa.bookloan.domain.saga.SagaAbortReason;
import msa.common.events.outbox.OutboxRecordableEvent;

import java.time.LocalDateTime;

public record LoanCancelRequestedInternalEvent(
        String sagaId,          // 모르면 null
        Long loanId,
        Long memberId,
        Long bookId,            // 모르면 null
        Long eventId,           // 스노우플레이크로 생성, 유니크
        Long aggregateVersion,  // 모르면 null (사가 로드해서 사용)
        SagaAbortReason reason,          // USER_CANCEL , TIMEOUT?
        LocalDateTime occurredAt
) implements OutboxRecordableEvent {}
