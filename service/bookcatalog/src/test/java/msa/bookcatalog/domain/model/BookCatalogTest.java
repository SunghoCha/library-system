package msa.bookcatalog.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class BookCatalogTest {

    private BookCatalog baseCatalog() {
        return BookCatalog.builder()
                .id(1L)
                .isbn13("9781234567890")
                .title("Old Title")
                .author("Old Author")
                .publisher("Old Publisher")
                .coverImageUrl("http://old.example/cover.jpg")
                .description("Old Desc")
                .publishDate(LocalDate.of(2026, 1, 1))
                .category(BookCategory.NOVEL)
                .bookType(BookType.STANDARD)
                .build();
    }

    private BookCatalog base() {
        return BookCatalog.builder()
                .id(10L)
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

    @Test
    @DisplayName("변경이 전혀 없으면 applyEditor는 false를 반환하고 아무 필드도 바뀌지 않는다")
    void applyEditor_noChanges_returnsFalse() {
        // given
        BookCatalog catalog = baseCatalog();
        BookCatalog.BookCatalogEditor editor = catalog.toEditorBuilder().build();

        // when
        boolean changed = catalog.applyEditor(editor);

        // then
        assertThat(changed).isFalse();
        assertThat(catalog.getTitle()).isEqualTo("Old Title");
        assertThat(catalog.getAuthor()).isEqualTo("Old Author");
        assertThat(catalog.getPublisher()).isEqualTo("Old Publisher");
        assertThat(catalog.getCoverImageUrl()).isEqualTo("http://old.example/cover.jpg");
        assertThat(catalog.getDescription()).isEqualTo("Old Desc");
        assertThat(catalog.getPublishDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(catalog.getCategory()).isEqualTo(BookCategory.NOVEL);
        assertThat(catalog.getBookType()).isEqualTo(BookType.STANDARD);
    }

    @DisplayName("부분 변경만 적용하면 해당 필드만 바뀌고 applyEditor는 true를 반환한다")
    @Test
    void applyEditor_partialUpdate_updatesOnlyChangedFields() {
        // given
        BookCatalog catalog = baseCatalog();
        BookCatalog.BookCatalogEditor editor = catalog.toEditorBuilder()
                .publisher("New Publisher")
                .description("New Desc")
                .build();

        // when
        boolean changed = catalog.applyEditor(editor);

        // then
        assertThat(changed).isTrue();
        assertThat(catalog.getPublisher()).isEqualTo("New Publisher");
        assertThat(catalog.getDescription()).isEqualTo("New Desc");

        // 나머지는 그대로
        assertThat(catalog.getTitle()).isEqualTo("Old Title");
        assertThat(catalog.getAuthor()).isEqualTo("Old Author");
        assertThat(catalog.getCoverImageUrl()).isEqualTo("http://old.example/cover.jpg");
        assertThat(catalog.getPublishDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(catalog.getCategory()).isEqualTo(BookCategory.NOVEL);
        assertThat(catalog.getBookType()).isEqualTo(BookType.STANDARD);
    }

    @Test
    @DisplayName("blank,null 입력은 빌더에서 무시되므로 기존 값이 유지되고 변경으로 간주되지 않는다")
    void applyEditor_blankOrNullAreIgnored_keepOriginal() {
        // given
        BookCatalog catalog = baseCatalog();
        BookCatalog.BookCatalogEditor editor = catalog.toEditorBuilder()
                .title("   ")            // blank -> 무시
                .author(null)            // null  -> 무시
                .publisher("")           // blank -> 무시
                .build();

        // when
        boolean changed = catalog.applyEditor(editor);

        // then
        assertThat(changed).isFalse();
        assertThat(catalog.getTitle()).isEqualTo("Old Title");
        assertThat(catalog.getAuthor()).isEqualTo("Old Author");
        assertThat(catalog.getPublisher()).isEqualTo("Old Publisher");
    }

    @Test
    @DisplayName("toEditorBuilder는 엔티티의 현재 값을 시드해 준다(빈 문자열도 그대로 유지)")
    void toEditorBuilder_seedsExistingValues_includingEmptyString() {
        // given: title이 빈 문자열인 엔티티
        BookCatalog catalog = BookCatalog.builder()
                .id(2L)
                .isbn13("9781111111111")
                .title("") // 빈 문자열
                .author("A")
                .publisher("P")
                .coverImageUrl("U")
                .description("D")
                .publishDate(LocalDate.of(2021, 2, 2))
                .category(BookCategory.IT)
                .bookType(BookType.NEW_RELEASE)
                .build();

        // when: title에 대해 아무 것도 세팅하지 않음(시드값 유지)
        BookCatalog.BookCatalogEditor editor = catalog.toEditorBuilder()
                .author("A") // 동일값
                .build();
        boolean changed = catalog.applyEditor(editor);

        // then: 변경 없음 + 빈 문자열 유지
        assertThat(changed).isFalse();
        assertThat(catalog.getTitle()).isEqualTo("");
        assertThat(catalog.getAuthor()).isEqualTo("A");
    }

    @Test
    @DisplayName("bookType 변경도 패치로 적용된다")
    void applyEditor_changeBookType() {
        // given
        BookCatalog catalog = baseCatalog(); // STANDARD
        BookCatalog.BookCatalogEditor editor = catalog.toEditorBuilder()
                .bookType(BookType.POPULAR)
                .build();

        // when
        boolean changed = catalog.applyEditor(editor);

        // then
        assertThat(changed).isTrue();
        assertThat(catalog.getBookType()).isEqualTo(BookType.POPULAR);
    }

    @Test
    @DisplayName("null editor면 IllegalArgumentException을 던진다")
    void applyEditor_null_throws() {
        BookCatalog catalog = baseCatalog();
        assertThatThrownBy(() -> catalog.applyEditor(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("editor must not be null");
    }

    @Test
    @DisplayName("여러 필드를 동시에 변경하면 applyEditor는 true이고 값들이 모두 갱신된다")
    void applyEditor_updatesMultipleFields() {
        // given
        BookCatalog c = base();

        BookCatalog.BookCatalogEditor editor = c.toEditorBuilder()
                .title("New Title")
                .author("New Author")
                .publisher("New Publisher")
                .coverImageUrl("http://new/cover.jpg")
                .description("New Desc")
                .publishDate(LocalDate.of(2022, 12, 31))
                .category(BookCategory.IT)
                .bookType(BookType.POPULAR)
                .build();

        // when
        boolean changed = c.applyEditor(editor);

        // then
        assertThat(changed).isTrue();
        assertThat(c.getTitle()).isEqualTo("New Title");
        assertThat(c.getAuthor()).isEqualTo("New Author");
        assertThat(c.getPublisher()).isEqualTo("New Publisher");
        assertThat(c.getCoverImageUrl()).isEqualTo("http://new/cover.jpg");
        assertThat(c.getDescription()).isEqualTo("New Desc");
        assertThat(c.getPublishDate()).isEqualTo(LocalDate.of(2022, 12, 31));
        assertThat(c.getCategory()).isEqualTo(BookCategory.IT);
        assertThat(c.getBookType()).isEqualTo(BookType.POPULAR);
    }

    @Test
    @DisplayName("빈 문자열은 빌더에서 무시되므로 기존 값 유지. 변경 아님")
    void builder_ignoresBlank_keepOriginal() {
        // given
        BookCatalog c = base();
        BookCatalog.BookCatalogEditor editor = c.toEditorBuilder()
                .title("   ")
                .publisher("")   // blank
                .author(null)    // null
                .build();

        // when
        boolean changed = c.applyEditor(editor);

        // then
        assertThat(changed).isFalse();
        assertThat(c.getTitle()).isEqualTo("Old Title");
        assertThat(c.getPublisher()).isEqualTo("Old Publisher");
        assertThat(c.getAuthor()).isEqualTo("Old Author");
    }

    @Test
    @DisplayName("엔티티가 빈 문자열을 가진 상태라면 toEditorBuilder 시드값으로 빈 문자열을 유지한다")
    void toEditorBuilder_seedsEmptyString() {
        BookCatalog c = BookCatalog.builder()
                .id(11L)
                .isbn13("9780000000002")
                .title("")
                .author("A")
                .publisher("P")
                .coverImageUrl("U")
                .description("D")
                .publishDate(LocalDate.of(2021, 2, 2))
                .category(BookCategory.IT)
                .bookType(BookType.NEW_RELEASE)
                .build();

        BookCatalog.BookCatalogEditor editor = c.toEditorBuilder().build();

        boolean changed = c.applyEditor(editor);

        assertThat(changed).isFalse();
        assertThat(c.getTitle()).isEqualTo("");
    }

    @Test
    @DisplayName("null이던 필드에 새 값이 들어오면 변경으로 처리된다 (description: null -> 값)")
    void nullToValue_countsAsChange() {
        BookCatalog c = BookCatalog.builder()
                .id(12L)
                .isbn13("9780000000003")
                .title("T")
                .author("A")
                .publisher("P")
                .coverImageUrl("U")
                .description(null)
                .publishDate(LocalDate.of(2020, 1, 1))
                .category(BookCategory.NOVEL)
                .bookType(BookType.STANDARD)
                .build();

        BookCatalog.BookCatalogEditor editor = c.toEditorBuilder()
                .description("Now I have description")
                .build();

        boolean changed = c.applyEditor(editor);

        assertThat(changed).isTrue();
        assertThat(c.getDescription()).isEqualTo("Now I have description");
    }

    @Test
    @DisplayName("isbn13은 에디터에 없으므로 applyEditor로는 변경되지 않는다")
    void isbnIsImmutableViaEditor() {
        BookCatalog c = base();
        String before = c.getIsbn13();

        BookCatalog.BookCatalogEditor editor = c.toEditorBuilder()
                .title("New Title Only")
                .build();

        boolean changed = c.applyEditor(editor);

        assertThat(changed).isTrue();
        assertThat(c.getIsbn13()).isEqualTo(before);
        assertThat(c.getTitle()).isEqualTo("New Title Only");
    }

    @Test
    @DisplayName("publishDate만 바뀌어도 변경으로 처리된다")
    void changePublishDate_only() {
        BookCatalog c = base();

        BookCatalog.BookCatalogEditor editor = c.toEditorBuilder()
                .publishDate(LocalDate.of(2024, 5, 5))
                .build();

        boolean changed = c.applyEditor(editor);

        assertThat(changed).isTrue();
        assertThat(c.getPublishDate()).isEqualTo(LocalDate.of(2024, 5, 5));
    }

    @Test
    @DisplayName("bookType만 바뀌어도 변경으로 처리된다")
    void changeBookType_only() {
        BookCatalog c = base();

        BookCatalog.BookCatalogEditor editor = c.toEditorBuilder()
                .bookType(BookType.NEW_RELEASE)
                .build();

        boolean changed = c.applyEditor(editor);

        assertThat(changed).isTrue();
        assertThat(c.getBookType()).isEqualTo(BookType.NEW_RELEASE);
    }
}