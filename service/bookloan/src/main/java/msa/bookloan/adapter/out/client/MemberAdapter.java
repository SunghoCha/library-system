package msa.bookloan.adapter.out.client;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.application.port.out.response.BlacklistResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberAdapter implements MemberPort {

    @Override
    public BlacklistResult checkBlacklist(Long memberId) {
        return null;
    }

    @Override
    public void addPoints(String sagaId, Long memberId, Long points) {

    }

    @Override
    public void deductPoints(String sagaId, Long memberId, Long points) {

    }
}
