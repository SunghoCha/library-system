package msa.bookloan.infra.projection;

import lombok.Getter;
import msa.common.domain.model.BookCategory;

@Getter
public class BookCatalogProjectionEditor {

    private String title;
    private String author;
    private BookCategory bookCategory;

    public BookCatalogProjectionEditor(String title, String author, BookCategory bookCategory) {
        this.title = title;
        this.author = author;
        this.bookCategory = bookCategory;
    }

    public static BookCatalogProjectionEditorBuilder builder() {
        return new BookCatalogProjectionEditorBuilder();
    }

    public static class BookCatalogProjectionEditorBuilder {
        private String title;
        private String author;
        private BookCategory bookCategory;

        public BookCatalogProjectionEditorBuilder title(final String title) {
            if (title != null && !title.isBlank()) {
                this.title = title;
            }
            return this;
        }

        public BookCatalogProjectionEditorBuilder author(final String author) {
            if (author != null && !author.isBlank()) {
                this.author = author;
            }
            return this;
        }

        public BookCatalogProjectionEditorBuilder bookCategory(final BookCategory bookCategory) {
            if (bookCategory != null) {
                this.bookCategory = bookCategory;
            }
            return this;
        }

        public BookCatalogProjectionEditor build() {
            return new BookCatalogProjectionEditor(title, author, bookCategory);
        }
    }

}
