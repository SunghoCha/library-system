package msa.bookloan.adapter.in.web.controller.dto.response;

import msa.bookloan.adapter.in.web.controller.dto.request.LoanCancelResult;

public record LoanCancelResponse(
        String loanId,
        String sagaId,
        String status,
        String statusUrl // 중복이긴한데 프론트 편의상 유지
) {
    public static LoanCancelResponse of(LoanCancelResult result, String statusUrl) {
        return new LoanCancelResponse(
                String.valueOf(result.loanId()),
                String.valueOf(result.sagaId()),
                result.loanStatus().name(),
                statusUrl
        );
    }
}
