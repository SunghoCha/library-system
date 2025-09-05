package msa.bookcatalog.infra.aladin;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "aladinClient", url = "http://www.aladin.co.kr/ttb/api")
public interface AladinClient {

    @GetMapping("/ItemList.aspx")
    String getBookList(
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

    @GetMapping("/ItemSearch.aspx")
    String searchBooks(
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
