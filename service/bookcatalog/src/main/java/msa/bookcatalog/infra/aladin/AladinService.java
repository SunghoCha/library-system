package msa.bookcatalog.infra.aladin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.aladin.config.AladinProperties;
import msa.bookcatalog.infra.aladin.model.ListQueryType;
import msa.bookcatalog.infra.aladin.model.QueryDate;
import msa.bookcatalog.infra.aladin.model.SearchQueryType;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
//@Service
@RequiredArgsConstructor
public class AladinService {

    private final AladinClient aladinClient;
    private final AladinProperties properties;

    public String getBookList(ListQueryType listQueryType) {
        return fetchBookList(listQueryType, null, null);
    }

    public String getBookListByCategory(ListQueryType listQueryType, int categoryId) {
        return fetchBookList(listQueryType, categoryId, null);
    }

    public String getBestSellerListByCategoryAndPeriod(int categoryId, QueryDate queryDate) {
        return fetchBookList(ListQueryType.Bestseller, categoryId, queryDate);
    }

    private String fetchBookList(ListQueryType listQueryType,
                                 Integer categoryId,
                                 QueryDate queryDate) {

        QueryDate qd = Objects.requireNonNullElse(queryDate, QueryDate.defaultDate());

        return aladinClient.getBookList(
                properties.getKey(),
                listQueryType.getValue(),
                properties.getDefaultMaxResults(),
                properties.getDefaultStart(),
                properties.getSearchTarget(),
                categoryId,
                qd.year(),
                qd.month(),
                qd.week(),
                properties.getFormat(),
                properties.getVersion()
        );
    }

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
    public String searchBooks(
            SearchQueryType queryType,
            String query,
            Integer maxResults,
            Integer start,
            Integer categoryId,
            String sort,
            String cover,
            String optResult
    ) {
        return fetchBookSearch(
                queryType.getValue(),
                query,
                maxResults,
                start,
                categoryId,
                sort,
                cover,
                optResult
        );
    }



    private String fetchBookSearch(
            String queryType,
            String query,
            Integer maxResults,
            Integer start,
            Integer categoryId,
            String sort,
            String cover,
            String optResult
    ) {
        return aladinClient.searchBooks(
                properties.getKey(),
                query,
                queryType,
                maxResults != null ? maxResults : properties.getDefaultMaxResults(),
                start      != null ? start      : properties.getDefaultStart(),
                properties.getSearchTarget(),
                categoryId,
                sort,
                cover,
                properties.getFormat(),
                properties.getVersion(),
                optResult
        );
    }

}
