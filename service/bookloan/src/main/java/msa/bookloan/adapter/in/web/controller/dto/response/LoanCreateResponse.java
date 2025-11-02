package msa.bookloan.adapter.in.web.controller.dto.response;

public record LoanCreateResponse(
        Long loanId,
        Long sagaId,
        String statusUrl    // 보통 "/loans/{loanId}"
) {
}
