package msa.bookcatalog.service.dto;

import msa.bookcatalog.domain.model.BookCatalog;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;

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
