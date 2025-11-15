package msa.member.web.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.common.domain.model.MemberGrade;
import msa.member.application.service.MemberService;
import msa.member.application.service.dto.MemberGradeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    // 여기에 인증인가 필요하긴할듯
    @GetMapping("{memberId}/grade")
    public ResponseEntity<MemberGradeResponse> getMemberGrade(@PathVariable Long memberId) {
        log.info("[MemberApi] 등급 조회 요청 수신: memberid={}", memberId);
        MemberGrade memberGrade = memberService.getMemberGrade(memberId);
        return ResponseEntity.ok(new MemberGradeResponse(String.valueOf(memberGrade)));
    }
}
