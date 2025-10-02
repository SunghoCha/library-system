package msa.bookcatalog.infra.batch;

import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.domain.model.BookCategory;
import msa.bookcatalog.domain.model.BookType;
import msa.bookcatalog.domain.policy.BookTypePolicy;
import msa.bookcatalog.infra.aladin.AladinService;
import msa.bookcatalog.infra.aladin.dto.AladinBookItemDto;
import msa.bookcatalog.infra.aladin.dto.AladinBookListResponse;
import msa.bookcatalog.infra.aladin.model.QueryDate;
import msa.bookcatalog.infra.batch.aladin.AladinBatchScheduler;
import msa.bookcatalog.infra.batch.aladin.BatchExecutionTracker;
import msa.bookcatalog.infra.batch.aladin.repository.BatchExecutionTrackerRepository;
import msa.bookcatalog.infra.batch.aladin.service.BookCatalogBatchService;
import msa.common.snowflake.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AladinBatchSchedulerTest {

    @Mock
    BatchExecutionTrackerRepository trackerRepository;

    @Mock
    BookCatalogBatchService batchService;

    @Mock
    AladinService aladinService;

    @Mock
    Snowflake snowflake;

    @Mock
    BookTypePolicy bookTypePolicy;

    // processBookCatalogChunk(...)에 넘어가는 청크를 캡처
    @Captor
    ArgumentCaptor<List<BookCatalog>> chunkCaptor;

    private AladinBatchScheduler scheduler;

    private Clock clock;

    private BatchExecutionTracker trackerMock;

    @BeforeEach
    void setUp() {
        // 2025-03-10T00:00:00Z 고정
        this.clock = Clock.fixed(
                LocalDate.parse("2025-03-10").atStartOfDay(ZoneId.of("UTC")).toInstant(),
                ZoneId.of("UTC")
        );
        this.scheduler = new AladinBatchScheduler(
                trackerRepository, batchService, aladinService, snowflake, bookTypePolicy, clock
        );

        // 트래커는 Mock으로: 마지막 실행일을 2025-03-10로 리턴하도록
        this.trackerMock = mock(BatchExecutionTracker.class);
        when(trackerRepository.findById(anyString()))
                .thenReturn(Optional.of(trackerMock));
        when(trackerMock.getLastExecutionDate())
                .thenReturn(LocalDate.of(2025, 3, 10));
    }

    private AladinBookItemDto dto(String isbn, String title, String author,
                                  LocalDate pubDate, String publisher, String cover, String desc, int catId) {
        return new AladinBookItemDto(
                title, author, pubDate, desc, isbn, cover, catId, null, publisher
        );
    }

    private AladinBookListResponse responseWith(List<AladinBookItemDto> items) {
        return new AladinBookListResponse(items);
    }


    @Test
    @DisplayName("단일 응답 -> DTO가 도메인으로 매핑되고, 청크 1회 호출 + 트래커가 targetDate(일주일 전)로 갱신된다")
    void load_one_response_maps_and_calls_once_and_updates_tracker() {
        // given
        // 첫 번째 API 호출에만 아이템이 나오고, 나머지는 빈 리스트 반환
        AladinBookItemDto dto = dto("9780000000001", "T", "A",
                LocalDate.of(2025, 3, 1), "P", "C", "D", 55890);
        AladinBookListResponse nonEmpty = responseWith(List.of(dto));
        AladinBookListResponse empty = responseWith(List.of());

        when(aladinService.getBestSellerListByCategoryAndPeriod(anyInt(), any(QueryDate.class)))
                .thenReturn(nonEmpty, empty);

        // 정책/ID
        when(bookTypePolicy.decide(eq(LocalDate.of(2025, 3, 1)), eq(LocalDate.now(clock))))
                .thenReturn(BookType.NEW_RELEASE);
        when(snowflake.nextId()).thenReturn(42L);

        // when
        scheduler.loadPastBestsellers();

        // then
        verify(batchService, times(1)).processBookCatalogChunk(chunkCaptor.capture());
        List<BookCatalog> converted = chunkCaptor.getValue();
        assertThat(converted).hasSize(1);
        BookCatalog b = converted.get(0);
        assertThat(b.getId()).isEqualTo(42L);
        assertThat(b.getIsbn13()).isEqualTo("9780000000001");
        assertThat(b.getTitle()).isEqualTo("T");
        assertThat(b.getAuthor()).isEqualTo("A");
        assertThat(b.getPublisher()).isEqualTo("P");
        assertThat(b.getCoverImageUrl()).isEqualTo("C");
        assertThat(b.getDescription()).isEqualTo("D");
        assertThat(b.getPublishDate()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(b.getCategory()).isEqualTo(BookCategory.IT);
        assertThat(b.getBookType()).isEqualTo(BookType.NEW_RELEASE);

        // targetDate = 2025-03-10 - 7일 = 2025-03-03
        verify(trackerMock).updateExecutionDate(LocalDate.of(2025, 3, 3));
        verify(trackerRepository).save(trackerMock);
    }


    @Test
    @DisplayName("중복 ISBN 제거 및 공백 ISBN 필터링이 적용된다")
    void deduplicate_and_blank_filter() {
        // given
        AladinBookItemDto d1 = dto("9780000000001", "A1", "Auth", LocalDate.of(2025,3,1), "P","C","D",1);
        AladinBookItemDto d2_dup = dto("9780000000001", "A1-dup", "Auth2", LocalDate.of(2025,3,2), "P2","C2","D2",1);
        AladinBookItemDto d3_blank = dto("   ", "B", "AuthB", LocalDate.of(2025,3,2), "PB","CB","DB",170);

        // 한 번만 아이템있고 나머지는 빈 리스트
        when(aladinService.getBestSellerListByCategoryAndPeriod(anyInt(), any(QueryDate.class)))
                .thenAnswer(inv -> responseWith(List.of(d1, d2_dup, d3_blank)))
                .thenReturn(responseWith(List.of())) // 이후 호출들
                .thenReturn(responseWith(List.of()))
                .thenReturn(responseWith(List.of()))
                .thenReturn(responseWith(List.of()))
                .thenReturn(responseWith(List.of()))
                .thenReturn(responseWith(List.of()))
                .thenReturn(responseWith(List.of()))
                .thenReturn(responseWith(List.of()));

        // 정책/ID (유일 1건만 도메인으로 변환됨)
        when(bookTypePolicy.decide(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BookType.STANDARD);
        when(snowflake.nextId()).thenReturn(100L);

        // when
        scheduler.loadPastBestsellers();

        // then
        verify(batchService).processBookCatalogChunk(chunkCaptor.capture());
        List<BookCatalog> list = chunkCaptor.getValue();
        assertThat(list).hasSize(1); // d1과 d2는 같은 ISBN → 1건, d3는 공백 ISBN → 제외

        BookCatalog one = list.get(0);
        assertThat(one.getId()).isEqualTo(100L);
        assertThat(one.getIsbn13()).isEqualTo("9780000000001");
        assertThat(one.getTitle()).isEqualTo("A1"); // 중복 제거 시 첫 항목 보존
        assertThat(one.getBookType()).isEqualTo(BookType.STANDARD);
    }

    @Test
    @DisplayName("대량 아이템 -> 청크 분할 호출, 첫 청크 실패 시 다음 청크는 계속 처리, 실패면 트래커 advance 안 함")
    void chunking_and_failure_does_not_advance_tracker() {
        // given
        // CHUNK_SIZE는 500이므로 500 + 20 = 520건 생성(ISBN 유니크)
        List<AladinBookItemDto> big = new ArrayList<>();
        for (int i = 0; i < 520; i++) {
            String isbn = "978" + String.format("%010d", i);
            big.add(dto(isbn, "T"+i, "A"+i, LocalDate.of(2025,3,1), "P", "C", "D", 1));
        }

        // 첫 호출에만 big, 나머지는 빈 리스트
        when(aladinService.getBestSellerListByCategoryAndPeriod(anyInt(), any(QueryDate.class)))
                .thenReturn(responseWith(big), responseWith(List.of()));

        when(bookTypePolicy.decide(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BookType.STANDARD);

        // nextId는 호출 횟수만큼 증가 값 리턴(아무 값이나)
        AtomicLong seq = new AtomicLong(100L);
        when(snowflake.nextId()).thenAnswer(inv -> seq.getAndIncrement());

        // 첫 청크에서 예외 → 이후 청크는 정상
        doThrow(new RuntimeException("예외 발생"))
                .doNothing()
                .when(batchService).processBookCatalogChunk(anyList());

        // when
        scheduler.loadPastBestsellers();

        // then
        verify(batchService, times(2)).processBookCatalogChunk(chunkCaptor.capture());
        List<List<BookCatalog>> chunks = chunkCaptor.getAllValues();
        assertThat(chunks.get(0)).hasSize(500);
        assertThat(chunks.get(1)).hasSize(20);

        // 실패가 있었으므로 트래커 저장 없음
        verify(trackerRepository, never()).save(any());
        verify(trackerMock, never()).updateExecutionDate(any());
    }
}

