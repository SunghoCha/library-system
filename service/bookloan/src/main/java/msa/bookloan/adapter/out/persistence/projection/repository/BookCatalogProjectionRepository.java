package msa.bookloan.adapter.out.persistence.projection.repository;

import msa.bookloan.adapter.out.persistence.projection.entity.BookCatalogProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookCatalogProjectionRepository extends JpaRepository<BookCatalogProjection, Long> {

}
