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
    private Integer categoryId;
    private String categoryName;

    public static BookCatalogEditorBuilder builder() {
        return new BookCatalogEditorBuilder();
    }

    public static class BookCatalogEditorBuilder {
        private String title;
        private String author;
        private String publisher;
        private String coverImageUrl;
        private String description;
        private Integer categoryId;
        private String categoryName;

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

        public BookCatalogEditorBuilder categoryId(Integer categoryId) {
            if (categoryId != null) {
                this.categoryId = categoryId;
            }
            return this;
        }

        public BookCatalogEditorBuilder categoryName(String categoryName) {
            if (categoryName != null && !categoryName.isBlank()) {
                this.categoryName = categoryName;
            }
            return this;
        }

        public BookCatalogEditor build() {
            return new BookCatalogEditor(title, author, publisher, coverImageUrl,
                    description, categoryId, categoryName);
        }

    }
}
