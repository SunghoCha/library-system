package msa.bookcatalog.repository;

import msa.bookcatalog.domain.model.BookCatalog;

import java.util.List;

public interface BookCatalogRepositoryCustom {
    void bulkUpsert(List<BookCatalog> bookCatalogs);
}
