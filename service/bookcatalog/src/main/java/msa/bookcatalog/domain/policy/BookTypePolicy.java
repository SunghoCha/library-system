package msa.bookcatalog.domain.policy;

import msa.bookcatalog.domain.model.BookType;

import java.time.LocalDate;

public interface BookTypePolicy {
    BookType decide(LocalDate publishDate, LocalDate today);
}
