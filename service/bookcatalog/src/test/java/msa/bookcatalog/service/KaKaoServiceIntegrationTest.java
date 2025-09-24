package msa.bookcatalog.service;

import msa.bookcatalog.infra.kakao.KaKaoService;
import msa.bookcatalog.infra.kakao.KakaoBookResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class KaKaoServiceIntegrationTest {

    @Autowired
    private KaKaoService kaKaoService;

//    @Test
//    void testBestSellerListFetch() {
//        List<KakaoBookResponse.Document> documents = kaKaoService.search("용기");
//        System.out.println(documents);
//    }




}

