package msa.bookloan.adapter.out.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "member-service")
public interface MemberServiceClient {

    @GetMapping("/api/members/{memberId}/grade")
    MemberGradeResponse getMemberGrade(@PathVariable("memberId") Long memberId);

    record MemberGradeResponse(
            Long memberId,
            String grade
    ) {}
}
