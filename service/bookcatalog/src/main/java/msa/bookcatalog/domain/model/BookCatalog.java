package msa.bookcatalog.domain.model;

import jakarta.persistence.*;
import lombok.*;
import msa.bookcatalog.infra.aladin.dto.AladinBookItemDto;
import msa.common.domain.base.BaseTimeEntity;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.Objects;

@Getter
@Entity
@Table(name = "book_catalog")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCatalog extends BaseTimeEntity implements Persistable<Long> {

    @Id
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 200)
    private String author;

    @Column(name = "pub_date")
    private LocalDate publishDate;

    @Column(name = "isbn13", length = 13, nullable = false, unique = true)
    private String isbn13;

    @Column(length = 100) // 출판사 길이 늘림
    private String publisher;

    @Column(name = "cover_url", length = 1000)
    private String coverImageUrl;

    @Lob
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32, nullable = false)
    private BookCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "book_type", length = 32, nullable = false)
    private BookType bookType;

    @Version
    private Long version;

    @Transient
    private boolean isNew = true;

    @Builder
    public BookCatalog(Long id, String title, String author, LocalDate publishDate,
                       String isbn13, String publisher, String coverImageUrl, String description,
                       BookCategory category, BookType bookType) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.publishDate = publishDate;
        this.isbn13 = isbn13;
        this.publisher = publisher;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
        this.category = category;
        this.bookType = bookType;
    }

    @Deprecated
    public static BookCatalog from(long id, AladinBookItemDto dto) {
        LocalDate pubDate = dto.pubDate();

        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        BookType initialBookType = (pubDate != null && pubDate.isAfter(thirtyDaysAgo))
                ? BookType.NEW_RELEASE
                : BookType.STANDARD;

        BookCategory category = BookCategory.fromId(dto.categoryId());

        return BookCatalog.builder()
                .id(id)
                .title(dto.title())
                .author(dto.author())
                .publishDate(dto.pubDate())
                .isbn13(dto.isbn13())
                .publisher(dto.publisher())
                .coverImageUrl(dto.cover())
                .description(dto.description())
                .category(category)
                .bookType(initialBookType)
                .build();
    }

    // 만약 빌드 사용시 기존 엔티티 필드에 ""있을 경우 null로 대체될 위험있어서 생성자로 했음
    public BookCatalogEditorBuilder toEditorBuilder() {
        return new BookCatalogEditorBuilder(
                title, author, publisher, coverImageUrl,
                description, bookType, publishDate, category
        );
    }

    @Deprecated
    public void edit(BookCatalogEditor editor) {
        this.title = editor.getTitle();
        this.author = editor.getAuthor();
        this.publisher = editor.getPublisher();
        this.coverImageUrl = editor.getCoverImageUrl();
        this.description = editor.getDescription();
        this.bookType = editor.getBookType();
        this.publishDate = editor.getPublishDate();
        this.category = editor.getCategory();

    }

    // 기존에는 완전히 같아도 그냥 업데이트했었는데 이렇게 하면 무의미한 UPDATED 이벤트가 발행되서 추가한 메서드
    public boolean applyEditor(BookCatalogEditor editor) {
        if (editor == null) throw new IllegalArgumentException("editor must not be null");
        boolean isChanged = false;

        if (!Objects.equals(this.title, editor.getTitle())) {
            this.title = editor.getTitle();
            isChanged = true;
        }

        if (!Objects.equals(this.author, editor.getAuthor())) {
            this.author = editor.getAuthor();
            isChanged = true;
        }

        if (!Objects.equals(this.publisher, editor.getPublisher())) {
            this.publisher = editor.getPublisher();
            isChanged = true;
        }

        if (!Objects.equals(this.coverImageUrl, editor.getCoverImageUrl())) {
            this.coverImageUrl = editor.getCoverImageUrl();
            isChanged = true;
        }

        if (!Objects.equals(this.description, editor.getDescription())) {
            this.description = editor.getDescription();
            isChanged = true;
        }


        if (!Objects.equals(this.publishDate, editor.getPublishDate())) {
            this.publishDate = editor.getPublishDate();
            isChanged = true;
        }

        if (!Objects.equals(this.category, editor.getCategory())) {
            this.category = editor.getCategory();
            isChanged = true;
        }

        if (!Objects.equals(this.bookType, editor.getBookType())) {
            this.bookType = editor.getBookType();
            isChanged = true;
        }

        return isChanged;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public Long getId() {
        return id;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class BookCatalogEditor {

        private final String title;
        private final String author;
        private final String publisher;
        private final String coverImageUrl;
        private final String description;
        private final BookType bookType;
        private final LocalDate publishDate;
        private final BookCategory category;

    }

    public static final class BookCatalogEditorBuilder {

        private String title;
        private String author;
        private String publisher;
        private String coverImageUrl;
        private String description;
        private BookType bookType;
        private LocalDate publishDate;
        private BookCategory category;

        private BookCatalogEditorBuilder(String title, String author, String publisher,
                                        String coverImageUrl, String description, BookType bookType,
                                        LocalDate publishDate, BookCategory category) {
            this.title = title;
            this.author = author;
            this.publisher = publisher;
            this.coverImageUrl = coverImageUrl;
            this.description = description;
            this.bookType = bookType;
            this.publishDate = publishDate;
            this.category = category;
        }

        private BookCatalogEditorBuilder() {}

        public BookCatalogEditorBuilder title(String title) {
            if (title != null && !title.isBlank()) {
                this.title = title;
            }
            return this;
        }

        public BookCatalogEditorBuilder author(String author) {
            if (author != null && !author.isBlank()) {
                this.author = author;
            }
            return this;
        }

        public BookCatalogEditorBuilder publisher(String publisher) {
            if (publisher != null && !publisher.isBlank()) {
                this.publisher = publisher;
            }
            return this;
        }

        public BookCatalogEditorBuilder coverImageUrl(String coverImageUrl) {
            if (coverImageUrl != null && !coverImageUrl.isBlank()) {
                this.coverImageUrl = coverImageUrl;
            }
            return this;
        }

        public BookCatalogEditorBuilder description(String description) {
            if (description != null && !description.isBlank()) {
                this.description = description;
            }
            return this;
        }

        public BookCatalogEditorBuilder bookType(BookType bookType) {
            if (bookType != null) this.bookType = bookType;
            return this;
        }

        public BookCatalogEditorBuilder publishDate(LocalDate publishDate) {
            if (publishDate != null) this.publishDate = publishDate;
            return this;
        }

        public BookCatalogEditorBuilder category(BookCategory category) {
            if (category != null) {
                this.category = category;
            }
            return this;
        }

        public BookCatalogEditor build() {
            return new BookCatalogEditor(title, author, publisher, coverImageUrl,
                    description, bookType, publishDate, category);
        }

    }
}


