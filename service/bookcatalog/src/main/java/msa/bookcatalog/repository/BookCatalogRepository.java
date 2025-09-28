package msa.bookcatalog.repository;

import msa.bookcatalog.domain.model.BookCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookCatalogRepository extends JpaRepository<BookCatalog, Long>, BookCatalogRepositoryCustom {

    boolean existsByIsbn13(String isbn13);
}
