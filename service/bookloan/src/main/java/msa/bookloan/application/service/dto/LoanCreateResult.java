package msa.bookloan.application.service.dto;

import msa.bookloan.domain.model.LoanProcessStatus;

public record LoanCreateResult(
        Long loanId,
        String sagaId,
        LoanProcessStatus status
) {
}
