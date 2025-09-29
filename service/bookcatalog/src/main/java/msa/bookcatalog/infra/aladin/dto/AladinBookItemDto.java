package msa.bookcatalog.infra.aladin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record AladinBookItemDto(
        String title,
        String author,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate pubDate,
        String description,
        String isbn13,
        String cover,
        int categoryId,
        String categoryName,
        String publisher
) {}
