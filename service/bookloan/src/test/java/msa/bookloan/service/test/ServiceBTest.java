package msa.bookloan.service.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ServiceBTest {

    @Autowired
    ServiceA serviceA;

    @Autowired
    MemberRepository memberRepository;

    @Test
    @DisplayName("ServiceA try-catch 예외처리 -> ServiceB(에러) 메서드 호출 시 롤백됨")
    void testTryCatchCommitInServiceB() {
        serviceA.callMethodInServiceBWithTryCatch(1L, "Updated Name B2");
        Member updatedMember = memberRepository.findById(1L).orElseThrow();
        assertEquals("Updated Name B2", updatedMember.getName());
    }

}