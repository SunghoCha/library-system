package msa.bookcatalog.infra.aladin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.infra.aladin.model.CategoryType;
import msa.bookcatalog.infra.aladin.model.QueryDate;
import msa.bookcatalog.repository.BookCatalogRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

// TODO : 나중에 활성화
@Slf4j
//@Component
@RequiredArgsConstructor
//public class DummyDataLoader implements ApplicationRunner {
public class DummyDataLoader  {

    private final BookCatalogRepository bookCatalogRepository;
    private final AladinService aladinService;
    private final ObjectMapper objectMapper;
    private static final int BATCH_SIZE = 50;

    //@Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        YearMonth start = YearMonth.of(2022, 1);
        YearMonth end = YearMonth.now();
        List<QueryDate> weeks = QueryDateGenerator.generateWeeklyQueryDates(start, end);

        for (CategoryType category : CategoryType.values()) {
            for (QueryDate queryDate : weeks) {
                String json = aladinService.getBestSellerListByCategoryAndPeriod(category.getId(), queryDate);
                JsonNode items = objectMapper.readTree(json).get("item");
                if (items == null || !items.isArray()) {
                    continue;
                }
                List<BookCatalog> bookCatalogList = new ArrayList<>();

                for (JsonNode node : items) {
                    long itemId = node.get("itemId").asLong();
                    String isbn13 = node.get("isbn13").asText();
                    if (bookCatalogRepository.existsByItemId(itemId) || bookCatalogRepository.existsByIsbn13(isbn13)) {
                        continue;
                    }
                    BookCatalog bookCatalog = BookCatalogMapper.fromJson(node, category);
                    bookCatalogList.add(bookCatalog);

                    if (bookCatalogList.size() >= BATCH_SIZE) {
                        bookCatalogRepository.saveAll(bookCatalogList);
                        bookCatalogList.clear();
                    }
                }
                if (!bookCatalogList.isEmpty()) {
                    bookCatalogRepository.saveAll(bookCatalogList);
                }
            }
        }
        log.info("주간 베스트셀러 더미 데이터 로딩 완료.");
    }
}
