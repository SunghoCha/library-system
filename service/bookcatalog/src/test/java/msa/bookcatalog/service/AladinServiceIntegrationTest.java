package msa.bookcatalog.service;

import msa.bookcatalog.infra.aladin.AladinService;
import msa.bookcatalog.infra.aladin.dto.AladinBookItemDto;
import msa.bookcatalog.infra.aladin.dto.AladinBookListResponse;
import msa.bookcatalog.infra.aladin.model.ListQueryType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class AladinServiceIntegrationTest {

    @Autowired
    private AladinService aladinService;

//    @Test
//    void testBestSellerListFetch() {
//        AladinBookListResponse bookList = aladinService.getBookList(ListQueryType.Bestseller);
//        List<AladinBookItemDto> items = bookList.item();
//        for (AladinBookItemDto item : items) {
//            System.out.println("item = " + item.isbn13());
//        }
//    }
//
//    @Test
//    void testBestSellerListWithCategory() {
//        String json = aladinService.getBookListByCategory(ListQueryType.Bestseller, 16034);
//        System.out.println(json);
//    }



}

