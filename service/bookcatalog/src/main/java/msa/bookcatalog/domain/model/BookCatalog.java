package msa.bookcatalog.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import msa.common.domain.base.BaseTimeEntity;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "book_catalog")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCatalog extends BaseTimeEntity {

    @Id
    private Long id;

    @Column(name = "item_id", nullable = false, unique = true)
    private Long itemId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String author;

    @Column(name = "pub_date")
    private LocalDate publishDate;

    @Column(name = "isbn13", length = 13, nullable = false, unique = true)
    private String isbn13;

    @Column(length = 30)
    private String publisher;

    @Column(name = "cover_url", length = 1000)
    private String coverImageUrl;

    @Lob
    @Column(nullable = true)
    private String description;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Builder
    public BookCatalog(Long id, Long itemId, String title, String author, LocalDate publishDate,
                       String isbn13, String publisher, String coverImageUrl, String description,
                       Integer categoryId, String categoryName) {
        this.id = id;
        this.itemId = itemId;
        this.title = title;
        this.author = author;
        this.publishDate = publishDate;
        this.isbn13 = isbn13;
        this.publisher = publisher;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

    public BookCatalogEditor.BookCatalogEditorBuilder toEditorBuilder() {
        return BookCatalogEditor.builder()
                .title(title)
                .author(author)
                .publisher(publisher)
                .coverImageUrl(coverImageUrl)
                .description(description)
                .categoryId(categoryId)
                .categoryName(categoryName);
    }

    public void edit(BookCatalogEditor editor) {
        this.title = editor.getTitle();
        this.author = editor.getAuthor();
        this.publisher = editor.getPublisher();
        this.coverImageUrl = editor.getCoverImageUrl();
        this.description = editor.getDescription();
        this.categoryId = editor.getCategoryId();
        this.categoryName = editor.getCategoryName();
    }

}

