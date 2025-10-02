package msa.bookcatalog.infra.batch.aladin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.domain.model.BookCategory;
import msa.bookcatalog.domain.model.BookType;
import msa.bookcatalog.domain.policy.BookTypePolicy;
import msa.bookcatalog.infra.aladin.AladinService;
import msa.bookcatalog.infra.aladin.dto.AladinBookItemDto;
import msa.bookcatalog.infra.aladin.dto.AladinBookListResponse;
import msa.bookcatalog.infra.aladin.model.QueryDate;
import msa.bookcatalog.infra.batch.aladin.repository.BatchExecutionTrackerRepository;
import msa.bookcatalog.infra.batch.aladin.service.BookCatalogBatchService;
import msa.common.snowflake.Snowflake;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aladin.batch.enabled", havingValue = "true", matchIfMissing = false)
public class AladinBatchScheduler {

    private static final String JOB_NAME = "ALADIN_BESTSELLER_LOADER";
    private static final int CHUNK_SIZE = 500;

    private final BatchExecutionTrackerRepository trackerRepository;
    private final BookCatalogBatchService batchService;
    private final AladinService aladinService;
    private final Snowflake snowflake;

    private final BookTypePolicy bookTypePolicy;
    private final Clock clock;

    private static final Map<String, Integer> CATEGORIES_TO_LOAD = Map.of(
            "소설", 1,
            "경제/경영", 170,
            "자기계발", 74,
            "인문", 656,
            "역사/문화", 76,
            "과학", 987,
            "컴퓨터/IT", 55890,
            "가정/요리/뷰티", 1230,
            "어린이", 1196
    );


    //매일 새벽 4시에 실행되어 과거의 베스트셀러 데이터를 수집.
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "PT5M",  // (1) 최대 락 유지 시간: 5분
            lockAtLeastFor = "PT30S"   // (2) 최소 락 유지 시간: 30초
    )
    public void loadPastBestsellers() {
        log.info("알라딘 과거 베스트셀러 데이터 수집 작업을 시작합니다.");

        // 마지막으로 실행했던 날짜 DB에서 조회
        // 첫 실행 시에는 오늘 + 1주로 설정해서 기존 1주씩 뒤로가는 로직에서 다시 오늘 구해지도록 함
        BatchExecutionTracker tracker = trackerRepository.findById(JOB_NAME)
                .orElse(new BatchExecutionTracker(JOB_NAME, LocalDate.now().plusWeeks(1)));

        // 이번에 조회할 목표 날짜 계산 (마지막 실행일보다 1주 전. 점점 일주일씩 뒤로 가면서 과거 베스트셀러 가져옴)
        LocalDate targetDate = tracker.getLastExecutionDate().minusWeeks(1);

        // 년, 월, 주차 정보 추출
        int year = targetDate.getYear();
        int month = targetDate.getMonthValue();
        int week = targetDate.get(WeekFields.of(Locale.KOREA).weekOfMonth());

        QueryDate queryDate = new QueryDate(year, month, week);
        log.info("{}년 {}월 {}주차 베스트셀러를 조회합니다.", year, month, week);

        List<AladinBookItemDto> allFetchedBookDtos = new ArrayList<>();

        // 각 대표 카테고리별로 API 호출
        for (Map.Entry<String, Integer> categoryEntry : CATEGORIES_TO_LOAD.entrySet()) {
            try {
                AladinBookListResponse response = aladinService.getBestSellerListByCategoryAndPeriod(categoryEntry.getValue(), queryDate);
                if (response != null && response.item() != null) {
                    allFetchedBookDtos.addAll(response.item());
                }
            } catch (Exception e) {
                log.info("카테고리 수집 실패 category={} code={}", categoryEntry.getKey(), categoryEntry.getValue(), e);
            }
        }

        if (allFetchedBookDtos.isEmpty()) {
            log.info("API에서 조회된 도서 정보가 없습니다. 다음 주기로 넘어갑니다.");
            tracker.updateExecutionDate(targetDate);
            trackerRepository.save(tracker); // 작업 진행 날짜를 업데이트해야 다음 주차로 넘어감
            return;
        }
        // isbn으로 중복제거 후 BatchItem으로 변환
        List<BookCatalog> bookCatalogs = toBookCatalogsDistinctByIsbn(allFetchedBookDtos);

        boolean anyChunkFailed = false;
        for (int i = 0; i < bookCatalogs.size(); i += CHUNK_SIZE) {
            List<BookCatalog> chunk = bookCatalogs.subList(i, Math.min(i + CHUNK_SIZE, bookCatalogs.size()));
            try {
                batchService.processBookCatalogChunk(chunk);
            } catch (Exception e) {
                anyChunkFailed = true;
                log.info("도서 카탈로그 청크 처리 실패. startIndex={}", i, e);
            }
        }

        if (!anyChunkFailed) {
            tracker.updateExecutionDate(targetDate);
            trackerRepository.save(tracker);
            log.debug("데이터 수집 작업 완료. 다음 작업 시 조회할 날짜: {}", targetDate.minusWeeks(1));
        } else {
            log.info("실패가 있어 트래커 advance 보류 — 같은 주차 재시도");
        }

    }

    private List<BookCatalog> toBookCatalogsDistinctByIsbn(List<AladinBookItemDto> allFetchedBookDtos) {
        return allFetchedBookDtos.stream()
                .filter(dto -> dto.isbn13() != null && !dto.isbn13().isBlank())
                .collect(Collectors.toMap(AladinBookItemDto::isbn13, Function.identity(), (a, b) -> a))
                .values().stream()
                .map(this::toDomain)
                .toList();
    }

    private BookCatalog toDomain(AladinBookItemDto dto) {
        BookType bookType = bookTypePolicy.decide(dto.pubDate(), LocalDate.now(clock));
        return BookCatalog.builder()
                .id(snowflake.nextId())
                .title(dto.title())
                .author(dto.author())
                .publishDate(dto.pubDate())
                .isbn13(dto.isbn13())
                .publisher(dto.publisher())
                .coverImageUrl(dto.cover())
                .description(dto.description())
                .category(BookCategory.fromId(dto.categoryId()))
                .bookType(bookType)
                .build();
    }

}