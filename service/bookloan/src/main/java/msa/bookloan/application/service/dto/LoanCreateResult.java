package msa.bookloan.application.service.dto;

import msa.bookloan.domain.model.LoanProcessStatus;

public record LoanCreateResult(
        Long loanId,
        Long sagaId,
        LoanProcessStatus status
) {
}
