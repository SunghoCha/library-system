package msa.bookloan.adapter.in.web.controller.dto.request;

import jakarta.validation.constraints.NotNull;

public record LoanCreateRequest(

        @NotNull(message = "회원 ID는 필수입니다.")
        Long memberId,

        @NotNull(message = "도서 ID는 필수입니다.")
        Long bookId
) {
}
