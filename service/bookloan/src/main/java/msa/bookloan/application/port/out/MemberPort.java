package msa.bookloan.application.port.out;

import msa.common.domain.model.MemberGrade;

public interface MemberPort {
    MemberGrade getGrade(Long memberId);
}
