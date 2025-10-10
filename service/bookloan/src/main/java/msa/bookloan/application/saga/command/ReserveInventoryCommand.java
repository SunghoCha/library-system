package msa.bookloan.application.saga.command;

import lombok.Builder;

@Builder
public record ReserveInventoryCommand(
        Long commandId,
        String sagaId,
        Long loanId,
        Long bookId,
        Long sourceAggregateVersion, // BookLoan @Version (출처 버전)
        Long causationEventId   // 커맨드 추적용 id
) { }
