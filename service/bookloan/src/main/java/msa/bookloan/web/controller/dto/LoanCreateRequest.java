package msa.bookloan.web.controller.dto;

import jakarta.validation.constraints.NotNull;

public record LoanCreateRequest(

        @NotNull(message = "회원 ID는 필수입니다.")
        Long memberId,

        @NotNull(message = "도서 ID는 필수입니다.")
        Long bookId
) {
}
