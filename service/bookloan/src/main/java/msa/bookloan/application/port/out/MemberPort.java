package msa.bookloan.application.port.out;

public interface MemberPort {
    BlacklistResult checkBlacklist(Long memberId);
    void addPoints(String sagaId, Long memberId, Long points);
    void deductPoints(String sagaId, Long memberId, Long points);
}
