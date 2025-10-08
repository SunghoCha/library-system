package msa.bookloan.adapter.in.web.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record LoanCartCreateRequest(
        @NotNull(message = "회원 ID는 필수입니다.")
        Long memberId,

        @NotEmpty(message = "하나 이상의 도서 ID가 필요합니다.")
        List<Long> bookIds
) {
}
