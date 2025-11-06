package msa.bookloan.domain.exception;

import msa.bookloan.domain.exception.errorCode.MemberErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class MemberServiceUnavailableException extends BusinessException {
    public MemberServiceUnavailableException(Long memberId) {
        super(MemberErrorCode.MEMBER_SERVICE_UNAVAILABLE, Map.of("memberId", memberId));
    }
}