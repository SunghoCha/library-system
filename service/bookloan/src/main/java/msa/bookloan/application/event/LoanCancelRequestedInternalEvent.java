package msa.bookloan.application.event;

import msa.bookloan.domain.saga.SagaAbortReason;
import msa.common.events.EventType;
import msa.common.events.outbox.OutboxRecordableEvent;

import java.time.LocalDateTime;

public record LoanCancelRequestedInternalEvent(
        Long eventId,           // 스노우플레이크로 생성, 유니크
        String sagaId,          // 모르면 null (처음 취소요청에서는 sagaId 알 수 없음)
        Long loanId,
        Long memberId,
        Long bookId,            // 취소 loan 정보에서 알 수 있음
        Long aggregateVersion,  // loan version (loan이 핵심이니 loan의 version정보)
        SagaAbortReason reason, // USER_CANCEL , TIMEOUT?
        LocalDateTime occurredAt
) {

}
