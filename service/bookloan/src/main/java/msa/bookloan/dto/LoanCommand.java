package msa.bookloan.dto;

import lombok.Builder;
import msa.common.domain.MemberGrade;

import java.util.List;

@Builder
public record LoanCommand(
        Long memberId,
        MemberGrade memberGrade,
        List<Long> bookIds

) { }
