package msa.bookcatalog.application.service.catalog.dto;

import java.time.LocalDate;

public record CreateBookCommand (

     String isbn13,
     String title,
     String author,
     String publisher,
     String description,
     String coverImageUrl,
     LocalDate publishDate,
     String bookType,
     Integer categoryId,
     String categoryName
){}
