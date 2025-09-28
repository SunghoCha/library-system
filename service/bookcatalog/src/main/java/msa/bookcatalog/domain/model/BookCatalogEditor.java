package msa.bookcatalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BookCatalogEditor {

    private String title;
    private String author;
    private String publisher;
    private String coverImageUrl;
    private String description;
    private BookType bookType;


    public static BookCatalogEditorBuilder builder() {
        return new BookCatalogEditorBuilder();
    }

    public static class BookCatalogEditorBuilder {
        private String title;
        private String author;
        private String publisher;
        private String coverImageUrl;
        private String description;
        private BookType bookType;

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

        public BookCatalogEditor build() {
            return new BookCatalogEditor(title, author, publisher, coverImageUrl, description, bookType);
        }

    }
}
