package msa.bookloan.adapter.in.web.controller.dto.response;

public record LoanCancelResponse(
        Long loanId,
        String sagaId,
        String status,
        String statusUrl
) { }
