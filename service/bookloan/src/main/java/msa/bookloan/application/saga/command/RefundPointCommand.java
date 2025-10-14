package msa.bookloan.application.saga.command;

import lombok.Builder;

@Builder
public record RefundPointCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long memberId,
        Long sourceAggregateVersion, // BookLoan의 @Version
        Long causationEventId        // 직전 내부 이벤트 ID
) implements SagaCommand { }