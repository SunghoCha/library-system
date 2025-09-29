package msa.bookcatalog.infra.aladin;

import msa.bookcatalog.infra.aladin.config.AladinFeignConfig;
import msa.bookcatalog.infra.aladin.dto.AladinBookListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "aladinClient",
        url = "http://www.aladin.co.kr/ttb/api",
        configuration = AladinFeignConfig.class)
public interface AladinClient {

    @GetMapping("/ItemList.aspx")
    AladinBookListResponse getBookList(
            @RequestParam("ttbkey") String ttbKey,
            @RequestParam("QueryType") String queryType,
            @RequestParam("MaxResults") int maxResults,
            @RequestParam("start") int start,
            @RequestParam("SearchTarget") String searchTarget,
            @RequestParam(value = "CategoryId", required = false) Integer categoryId,
            @RequestParam(value = "Year",      required = false) Integer year,
            @RequestParam(value = "Month",     required = false) Integer month,
            @RequestParam(value = "Week",      required = false) Integer week,
            @RequestParam("output") String output,
            @RequestParam("Version") String version
    );


    /**
     * 키워드 검색을 통해 책 목록을 가져옵니다.
     *
     * @param queryType   검색 타입 (Keyword, Title, Author, Publisher)
     * @param query       검색어
     * @param maxResults  한 페이지 결과 수 (옵션, 기본 10)
     * @param start       시작 인덱스 (옵션, 기본 1)
     * @param categoryId  분야 필터 (옵션)
     * @param sort        정렬 기준 (옵션)
     * @param cover       표지 크기 (옵션)
     * @param optResult   부가 정보 (ebookList, usedList, reviewList 등, 옵션)
     */
    @GetMapping("/ItemSearch.aspx")
    AladinBookListResponse  searchBooks(
            @RequestParam("ttbkey") String ttbKey,
            @RequestParam("Query") String query,
            @RequestParam("QueryType") String queryType,
            @RequestParam("MaxResults") int maxResults,
            @RequestParam("start") int start,
            @RequestParam("SearchTarget") String searchTarget,
            @RequestParam(value = "CategoryId", required = false) Integer categoryId,
            @RequestParam(value = "Sort", required = false) String sort,
            @RequestParam(value = "Cover", required = false) String cover,
            @RequestParam("output") String output,
            @RequestParam("Version") String version,
            @RequestParam(value = "OptResult", required = false) String optResult
    );
}
