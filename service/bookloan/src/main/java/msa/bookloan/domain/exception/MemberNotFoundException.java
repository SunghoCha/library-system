package msa.bookloan.domain.exception;

import msa.bookloan.domain.exception.errorCode.MemberErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class MemberNotFoundException extends BusinessException {
    public MemberNotFoundException(Long memberId) {
        super(MemberErrorCode.MEMBER_NOT_FOUND, Map.of("memberId", memberId));
    }
}
