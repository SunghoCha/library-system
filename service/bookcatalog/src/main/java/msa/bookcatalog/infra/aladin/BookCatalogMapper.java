package msa.bookcatalog.infra.aladin;

import com.fasterxml.jackson.databind.JsonNode;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.infra.aladin.model.CategoryType;
import msa.common.snowflake.Snowflake;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BookCatalogMapper {

    private static final Snowflake snowflake = new Snowflake();

    public static BookCatalog fromJson(JsonNode node, CategoryType categoryType) {
        return BookCatalog.builder()
                .id(snowflake.nextId())
                .itemId(node.get("itemId").asLong())
                .title(node.get("title").asText())
                .author(node.get("author").asText())
                .publishDate(LocalDate.parse(node.get("pubDate").asText()))
                .isbn13(node.get("isbn13").asText())
                .publisher(node.get("publisher").asText())
                .coverImageUrl(node.get("cover").asText())
                .description(node.get("description").asText())
                .categoryId(categoryType.getId())
                .categoryName(categoryType.getDisplayName())
                .build();
    }
}
