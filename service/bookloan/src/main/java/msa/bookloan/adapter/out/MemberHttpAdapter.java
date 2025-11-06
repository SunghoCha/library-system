package msa.bookloan.adapter.out;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookloan.adapter.out.client.MemberServiceClient;
import msa.bookloan.application.port.out.MemberPort;
import msa.bookloan.application.port.out.response.BlacklistResult;
import msa.bookloan.domain.exception.MemberNotFoundException;
import msa.bookloan.domain.exception.MemberServiceUnavailableException;
import msa.common.domain.model.MemberGrade;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberHttpAdapter implements MemberPort {

    private final MemberServiceClient memberServiceClient;

    @Override
    public MemberGrade getGrade(Long memberId) {

        try {
            log.debug("[MemberPort] 등급 조회 시도: memberId={}", memberId);
            MemberServiceClient.MemberGradeResponse response =
                    memberServiceClient.getMemberGrade(memberId);
            MemberGrade memberGrade = MemberGrade.valueOf(response.grade());
            log.info("[MemberPort] 등급 조회 성공: memberId={}, grade={}", memberId, memberGrade.name());
            return memberGrade;
        } catch (FeignException e) {
            int status = e.status();
            if (status == 404) {
                log.info("[MemberPort] GRADE_FETCH 회원없음: memberId={}", memberId);
                throw new MemberNotFoundException(memberId);
            }
            log.warn("[MemberPort] GRADE_FETCH 실패: memberId={}, status={}", memberId, status);
            throw new MemberServiceUnavailableException(memberId);

        } catch (IllegalArgumentException | NullPointerException ex) { // 응답 계약 불일치
            log.warn("[MemberPort] GRADE_FETCH 계약불일치: memberId={}", memberId);
            throw new MemberServiceUnavailableException(memberId);
        }
    }
}
