package msa.bookcatalog.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import msa.bookcatalog.infra.aladin.dto.AladinBookItemDto;
import msa.common.domain.base.BaseTimeEntity;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "book_catalog")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCatalog extends BaseTimeEntity implements Persistable<Long> {

    @Id
    private Long id;

    @Column(nullable = false, length = 255) // 제목 길이를 넉넉하게 늘림
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

    @Lob // 긴 텍스트를 위한 설정
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

    public static BookCatalog from(long id, AladinBookItemDto dto) {
        LocalDate pubDate = dto.pubDate();

        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        BookType initialBookType = (pubDate != null && pubDate.isAfter(thirtyDaysAgo))
                ? BookType.NEW_RELEASE
                : BookType.STANDARD;

        BookCategory category = BookCategory.fromId(dto.categoryId());

        BookCatalog newBook = BookCatalog.builder()
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

        return newBook;
    }

    public BookCatalogEditor.BookCatalogEditorBuilder toEditorBuilder() {
        return BookCatalogEditor.builder()
                .title(title)
                .author(author)
                .publisher(publisher)
                .coverImageUrl(coverImageUrl)
                .description(description)
                .bookType(bookType);

    }

    public void edit(BookCatalogEditor editor) {
        this.title = editor.getTitle();
        this.author = editor.getAuthor();
        this.publisher = editor.getPublisher();
        this.coverImageUrl = editor.getCoverImageUrl();
        this.description = editor.getDescription();
        this.bookType = editor.getBookType();

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
}


