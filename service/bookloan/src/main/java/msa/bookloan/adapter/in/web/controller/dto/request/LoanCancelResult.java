package msa.bookloan.adapter.in.web.controller.dto.request;

public record LoanCancelResult(
        Long loanId,
        String sagaId
) { }