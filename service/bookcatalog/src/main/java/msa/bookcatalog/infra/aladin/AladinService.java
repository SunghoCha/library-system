package msa.bookcatalog.infra.aladin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.infra.aladin.config.AladinProperties;
import msa.bookcatalog.infra.aladin.dto.AladinBookListResponse;
import msa.bookcatalog.infra.aladin.dto.AladinSearchRequest;
import msa.bookcatalog.infra.aladin.model.ListQueryType;
import msa.bookcatalog.infra.aladin.model.QueryDate;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AladinService {

    private final AladinClient aladinClient;
    private final AladinProperties properties;

    public AladinBookListResponse getBookList(ListQueryType listQueryType) {
        return fetchBookList(listQueryType, null, null);
    }

    public AladinBookListResponse  getBookListByCategory(ListQueryType listQueryType, int categoryId) {
        return fetchBookList(listQueryType, categoryId, null);
    }

    public AladinBookListResponse  getBestSellerListByCategoryAndPeriod(int categoryId, QueryDate queryDate) {
        return fetchBookList(ListQueryType.Bestseller, categoryId, queryDate);
    }

    private AladinBookListResponse  fetchBookList(ListQueryType listQueryType,
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


    public AladinBookListResponse searchBooks(AladinSearchRequest request) {
        return aladinClient.searchBooks(
                properties.getKey(),
                request.query(),
                request.queryType().getValue(),
                // Optional 파라미터는 null 체크 후 기본값 사용
                request.maxResults() != null ? request.maxResults() : properties.getDefaultMaxResults(),
                request.start()      != null ? request.start()      : properties.getDefaultStart(),
                properties.getSearchTarget(),
                request.categoryId(),
                request.sort(),
                request.cover(),
                properties.getFormat(),
                properties.getVersion(),
                request.optResult()
        );
    }


}
