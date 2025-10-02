package msa.bookcatalog.service.dto;

import java.time.LocalDate;

public record UpdateBookCommand(

        Long bookCatalogId,
        String title,
        String author,
        String publisher,
        String description,
        String coverImageUrl,
        LocalDate publishDate,
        String bookType
) { }
