package msa.bookloan.service.dto;

import lombok.Builder;
import msa.common.domain.model.MemberGrade;

import java.util.List;

@Builder
public record LoanCommand(
        Long memberId,
        MemberGrade memberGrade,
        List<Long> bookIds
) { }
