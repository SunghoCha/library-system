package msa.bookloan.application.event;

import msa.bookloan.domain.saga.SagaAbortReason;

import java.time.LocalDateTime;

public record LoanCancelRequestedInternalEvent(
        Long eventId,           // 스노우플레이크로 생성, 유니크
        Long sagaId,          //
        Long loanId,
        Long memberId,
        Long bookId,            // 취소 loan 정보에서 알 수 있음
        Long aggregateVersion,  // loan version (loan이 핵심이니 loan의 version정보)
        SagaAbortReason reason, // USER_CANCEL , TIMEOUT?
        LocalDateTime occurredAt
) {

}
