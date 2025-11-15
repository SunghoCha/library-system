package msa.member.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import msa.common.domain.model.MemberGrade;
import msa.member.application.service.MemberService;
import msa.member.application.service.dto.MemberGradeResponse;
import msa.member.application.service.exception.MemberNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemberController.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    private MemberService memberService;

    @Test
    @DisplayName("회원 등급 조회 성공")
    void getMemberGrade() throws Exception {
        // given
        Long memberId = 1L;
        MemberGrade grade = MemberGrade.GOLD;
        MemberGradeResponse memberGradeResponse = new MemberGradeResponse(grade.name());

        given(memberService.getMemberGrade(memberId)).willReturn(grade);

        // when
        mockMvc.perform(get("/api/v1/members/{memberId}/grade", memberId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(memberGradeResponse.grade()))
                .andDo(print());


    }

    @Test
    @DisplayName("회원 등급 조회 실패 - 회원 없음")
    void getMemberGrade_notFound() throws Exception {
        // given
        Long memberId = 99L;

        given(memberService.getMemberGrade(memberId)).willThrow(new MemberNotFoundException(memberId));

        // when & then
        mockMvc.perform(get("/api/v1/members/{memberId}/grade", memberId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MB-001"))
                .andExpect(jsonPath("$.errorMessage").value("회원을 찾을 수 없습니다."))
                .andDo(print());


    }

}