package msa.bookloan.adapter.in.web.controller.dto.response;

import msa.bookloan.domain.model.LoanProcessStatus;

public record LoanCreateResponse(
        Long loanId,
        Long sagaId,
        LoanProcessStatus status,
        String statusUrl    // 보통 "/loans/{loanId}"
) {
}
