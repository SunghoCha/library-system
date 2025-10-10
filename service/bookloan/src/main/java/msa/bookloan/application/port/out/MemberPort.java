package msa.bookloan.application.port.out;

import msa.bookloan.application.port.out.response.BlacklistResult;

public interface MemberPort {
    BlacklistResult checkBlacklist(Long memberId);
    void addPoints(String sagaId, Long memberId, Long points);
    void deductPoints(String sagaId, Long memberId, Long points);
}
