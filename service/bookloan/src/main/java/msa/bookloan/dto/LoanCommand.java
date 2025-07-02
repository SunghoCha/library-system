package msa.bookloan.dto;

import lombok.Builder;
import msa.common.domain.MemberGrade;

@Builder
public record LoanCommand(
        Long memberId,
        MemberGrade memberGrade,
        Long bookId

) { }
