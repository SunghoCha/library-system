package msa.bookloan.application.saga.command;

import lombok.Builder;

@Builder
public record ReleaseInventoryCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long bookId,
        Long sourceAggregateVersion, // BookLoan의 @Version
        Long causationEventId        // 직전 내부 이벤트 ID
) implements SagaCommand { }
