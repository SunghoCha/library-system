package msa.bookloan.infra.projection;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import msa.common.domain.model.BookCategory;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCatalogProjection {

    @Id
    @Column(name = "book_id")
    private Long bookId;
    private String title;
    private String author;
    @Enumerated(EnumType.STRING)
    private BookCategory bookCategory;

    @Builder
    public BookCatalogProjection(Long bookId, String title, String author, BookCategory bookCategory) {
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.bookCategory = bookCategory;
    }

    public void updateCategory(BookCategory newCategory) {
        this.bookCategory = newCategory;
    }

    public BookCatalogProjectionEditor.BookCatalogProjectionEditorBuilder toEditorBuilder() {
        return BookCatalogProjectionEditor.builder()
                .title(title)
                .author(author)
                .bookCategory(bookCategory);
    }

    public void edit(BookCatalogProjectionEditor editor) {
        this.title = editor.getTitle();
        this.author = editor.getAuthor();
        this.bookCategory = editor.getBookCategory();
    }
}
