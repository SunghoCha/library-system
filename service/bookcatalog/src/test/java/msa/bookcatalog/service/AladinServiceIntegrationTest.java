package msa.bookcatalog.service;

import msa.bookcatalog.infra.aladin.AladinService;
import msa.bookcatalog.infra.aladin.model.ListQueryType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AladinServiceIntegrationTest {

    @Autowired
    private AladinService aladinService;

//    @Test
//    void testBestSellerListFetch() {
//        String json = aladinService.getBookList(ListQueryType.Bestseller);
//        System.out.println(json);
//        Assertions.assertTrue(json.contains("item")); // 간단한 응답 검증
//    }
//
//    @Test
//    void testBestSellerListWithCategory() {
//        String json = aladinService.getBookListByCategory(ListQueryType.Bestseller, 16034);
//        System.out.println(json);
//    }



}

