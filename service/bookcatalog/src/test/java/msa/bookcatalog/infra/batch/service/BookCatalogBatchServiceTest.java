package msa.bookcatalog.infra.batch.service;

import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.domain.model.BookCategory;
import msa.bookcatalog.domain.model.BookType;
import msa.bookcatalog.infra.batch.aladin.BookCatalogEventMapper;
import msa.bookcatalog.infra.batch.aladin.service.BookCatalogBatchService;
import msa.bookcatalog.infra.outbox.recorder.EventRecorder;
import msa.bookcatalog.repository.BookCatalogRepository;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCatalogBatchServiceTest {

    @Mock
    private EventRecorder eventRecorder;

    @Mock
    private BookCatalogRepository bookCatalogRepository;

    @Mock
    private BookCatalogEventMapper bookCatalogEventMapper;

    @InjectMocks
    private BookCatalogBatchService service;

    @Captor ArgumentCaptor<List<BookCatalog>> saveCaptor;

    private BookCatalog existing() {
        return BookCatalog.builder()
                .id(1L)
                .isbn13("9780000000001")
                .title("Old Title")
                .author("Old Author")
                .publisher("Old Publisher")
                .coverImageUrl("http://old/cover.jpg")
                .description("Old Desc")
                .publishDate(LocalDate.of(2020, 1, 1))
                .category(BookCategory.NOVEL)
                .bookType(BookType.STANDARD)
                .build();
    }

    private BookCatalog incomingNew(String isbn) {
        return BookCatalog.builder()
                .id(100L)
                .isbn13(isbn)
                .title("New Title")
                .author("New Author")
                .publisher("New Publisher")
                .coverImageUrl("http://new/cover.jpg")
                .description("New Desc")
                .publishDate(LocalDate.of(2024, 6, 1))
                .category(BookCategory.IT)
                .bookType(BookType.NEW_RELEASE)
                .build();
    }

    private BookCatalog incomingForUpdateSameIsbn() {
        return BookCatalog.builder()
                .id(99L) // 신규 id지만 ISBN이 같으므로 업데이트 대상
                .isbn13("9780000000001")
                .title("Updated Title")
                .author("Updated Author")
                .publisher("Updated Publisher")
                .coverImageUrl("http://updated/cover.jpg")
                .description("Updated Desc")
                .publishDate(LocalDate.of(2024, 5, 5))
                .category(BookCategory.IT)
                .bookType(BookType.POPULAR)
                .build();
    }

    @Test
    @DisplayName("신규 + 기존(변경됨) 혼재 시: 신규만 saveAll, 기존은 더티체킹, 이벤트는 CREATED/UPDATED로 각각 생성")
    void mixed_new_and_existing_changed() {
        // given
        BookCatalog existing = existing();
        BookCatalog updateIncoming = incomingForUpdateSameIsbn();
        BookCatalog createIncoming = incomingNew("9780000000002");

        List<BookCatalog> chunk = List.of(updateIncoming, createIncoming);

        // 기존 조회 결과: existing 한 건만 반환
        when(bookCatalogRepository.findAllByIsbn13In(anyList()))
                .thenReturn(List.of(existing));

        // 이벤트 매퍼는 더미 이벤트 리턴 (내용은 중요치 않음)
        when(bookCatalogEventMapper.toEventFrom(any(BookCatalog.class), any(EventType.class)))
                .thenReturn(mock(BookCatalogChangedEvent.class));

        // when
        service.processBookCatalogChunk(chunk);

        // then
        // 1) 신규만 saveAll에 태워졌는지 확인
        verify(bookCatalogRepository).saveAll(saveCaptor.capture());
        List<BookCatalog> savedList = saveCaptor.getValue();
        assertThat(savedList).hasSize(1);
        assertThat(savedList.get(0).getIsbn13()).isEqualTo("9780000000002"); // 신규만

        // 2) flush는 항상 호출
        verify(bookCatalogRepository).flush();

        // 3) 이벤트 매퍼 호출 검증
        verify(bookCatalogEventMapper).toEventFrom(eq(existing), eq(EventType.UPDATED));
        verify(bookCatalogEventMapper).toEventFrom(eq(createIncoming), eq(EventType.CREATED));

        // 4) 이벤트 저장 한 번 호출 (개수는 매퍼 호출 수와 동일)
        verify(eventRecorder, times(1)).saveAll(anyList());
        verifyNoMoreInteractions(eventRecorder);
    }

    @Test
    @DisplayName("기존 있음 + 변경 없음: saveAll 호출 없음, UPDATED 이벤트 없음, flush는 호출")
    void existing_no_change() {
        // given
        BookCatalog existing = existing();

        // incoming을 기존과 '동일 값'으로 구성
        BookCatalog sameIncoming = BookCatalog.builder()
                .id(200L) // id는 달라도 ISBN이 같으면 업데이트 후보
                .isbn13(existing.getIsbn13())
                .title(existing.getTitle())
                .author(existing.getAuthor())
                .publisher(existing.getPublisher())
                .coverImageUrl(existing.getCoverImageUrl())
                .description(existing.getDescription())
                .publishDate(existing.getPublishDate())
                .category(existing.getCategory())
                .bookType(existing.getBookType())
                .build();

        when(bookCatalogRepository.findAllByIsbn13In(anyList()))
                .thenReturn(List.of(existing));

        // when
        service.processBookCatalogChunk(List.of(sameIncoming));

        // then
        // 신규가 없으므로 saveAll 호출 없어야 함
        verify(bookCatalogRepository, never()).saveAll(anyList());
        // flush는 항상 호출
        verify(bookCatalogRepository).flush();
        // 이벤트 매퍼/레코더는 호출되지 않음
        verifyNoInteractions(eventRecorder, bookCatalogEventMapper);
    }

    @Test
    @DisplayName("incoming의 공백/널 필드는 빌더에서 무시되어 변경 없음으로 처리")
    void blank_fields_are_ignored_by_builder() {
        // given
        BookCatalog existing = existing();

        // title에 공백, author에 null을 전달 (빌더가 무시)
        BookCatalog incoming = BookCatalog.builder()
                .id(300L)
                .isbn13(existing.getIsbn13())
                .title("   ")   // 공백 -> 무시
                .author(null)  // null -> 무시
                .publisher("") // 빈문자열 -> 무시
                .coverImageUrl(null)
                .description(null)
                .publishDate(null)
                .category(null)
                .bookType(null)
                .build();

        when(bookCatalogRepository.findAllByIsbn13In(anyList()))
                .thenReturn(List.of(existing));

        // when
        service.processBookCatalogChunk(List.of(incoming));

        // then
        verify(bookCatalogRepository, never()).saveAll(anyList());
        verify(bookCatalogRepository).flush();
        verifyNoInteractions(eventRecorder, bookCatalogEventMapper);

        // 기존 엔티티 값이 그대로 유지되었는지 체크
        assertThat(existing.getTitle()).isEqualTo("Old Title");
        assertThat(existing.getAuthor()).isEqualTo("Old Author");
        assertThat(existing.getPublisher()).isEqualTo("Old Publisher");
    }

    @Test
    @DisplayName("신규만 들어온 경우: 모두 saveAll 대상, 이벤트는 CREATED로만 생성")
    void only_new_books() {
        // given
        BookCatalog n1 = incomingNew("9780000000100");
        BookCatalog n2 = incomingNew("9780000000200");
        List<BookCatalog> chunk = List.of(n1, n2);

        // 기존 없음
        when(bookCatalogRepository.findAllByIsbn13In(anyList()))
                .thenReturn(new ArrayList<>());

        // 이건 없어도 되긴 할 듯
        when(bookCatalogEventMapper.toEventFrom(any(BookCatalog.class), eq(EventType.CREATED)))
                .thenReturn(mock(BookCatalogChangedEvent.class));

        // when
        service.processBookCatalogChunk(chunk);

        // then
        verify(bookCatalogRepository).saveAll(saveCaptor.capture());
        List<BookCatalog> saved = saveCaptor.getValue();
        assertThat(saved).containsExactlyInAnyOrder(n1, n2);

        verify(bookCatalogRepository).flush();

        // CREATED만 두 번
        verify(bookCatalogEventMapper, times(2))
                .toEventFrom(any(BookCatalog.class), eq(EventType.CREATED));
        // UPDATED 호출은 없어야 함
        verify(bookCatalogEventMapper, never())
                .toEventFrom(any(BookCatalog.class), eq(EventType.UPDATED));

        verify(eventRecorder).saveAll(anyList());
    }



}