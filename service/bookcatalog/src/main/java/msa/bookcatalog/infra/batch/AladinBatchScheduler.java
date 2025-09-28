package msa.bookcatalog.infra.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.infra.aladin.AladinService;
import msa.bookcatalog.infra.aladin.dto.AladinBookItemDto;
import msa.bookcatalog.infra.aladin.dto.AladinBookListResponse;
import msa.bookcatalog.infra.aladin.model.QueryDate;
import msa.bookcatalog.repository.BookCatalogRepository;
import msa.common.snowflake.Snowflake;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AladinBatchScheduler {

    private static final String JOB_NAME = "ALADIN_BESTSELLER_LOADER";
    private static final int CHUNK_SIZE = 500;

    private final BatchExecutionTrackerRepository trackerRepository;
    private final BookCatalogRepository bookCatalogRepository;
    private final BookCatalogBatchService batchService;
    private final AladinService aladinService;
    private final Snowflake snowflake;

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
    @Transactional
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
            AladinBookListResponse response = aladinService.getBestSellerListByCategoryAndPeriod(categoryEntry.getValue(), queryDate);
            if (response != null && response.item() != null) {
                allFetchedBookDtos.addAll(response.item());
            }
        }

        if (allFetchedBookDtos.isEmpty()) {
            log.info("API에서 조회된 도서 정보가 없습니다.");
            return; // 이후 로직 실행 불필요
        }

        List<BookCatalog> booksToUpsert = allFetchedBookDtos.stream()
                .filter(item -> item.isbn13() != null && !item.isbn13().isBlank())
                .collect(Collectors.toMap(
                        AladinBookItemDto::isbn13,
                        dto -> dto,
                        (dto1, dto2) -> dto2
                ))
                .values().stream()
                .map(dto -> BookCatalog.from(snowflake.nextId(), dto))
                .toList();

//        if (!booksToUpsert.isEmpty()) {
//            bookCatalogRepository.bulkUpsert(booksToUpsert);
//            log.info("총 {}개의 도서 정보를 Upsert 했습니다.", booksToUpsert.size());
//        } else {
//            log.info("이번 주차에는 저장할 새로운 도서 정보가 없습니다.");
//        }

        // --- 청크 분할 및 위임 ---
        for (int i = 0; i < booksToUpsert.size(); i += CHUNK_SIZE) {
            List<BookCatalog> chunk = booksToUpsert.subList(i, Math.min(i + CHUNK_SIZE, booksToUpsert.size()));
            try {
                // 각 청크 처리를 새로운 서비스에 위임
                batchService.processBookCatalogChunk(chunk);
            } catch (Exception e) {
                // 특정 청크 실패 시 에러 로깅. 루프는 계속 진행할 수도, 멈출 수도 있음.
                log.error("도서 카탈로그 청크 처리 중 에러 발생. 청크 시작 인덱스: {}", i, e);
            }
        }

        // 작업 끝난 후, 마지막 실행 날짜를 오늘 조회한 날짜로 업데이트
        tracker.updateExecutionDate(targetDate);
        trackerRepository.save(tracker);

        log.info("데이터 수집 작업 완료. 다음 작업 시 조회할 날짜: {}", targetDate.minusWeeks(1));
    }
}