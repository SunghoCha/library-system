package msa.bookloan.infra.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookCatalogProjectionRepository extends JpaRepository<BookCatalogProjection, Long> {
    Optional<BookCatalogProjection> findByBookId(Long bookId);

    void deleteByBookId(Long bookId);
}
