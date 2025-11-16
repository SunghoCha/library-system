package msa.bookloan.adapter.in.web.controller.dto.request;

import msa.bookloan.domain.model.LoanStatus;

public record LoanCancelResult(
        Long loanId,
        Long sagaId,
        LoanStatus loanStatus
) { }