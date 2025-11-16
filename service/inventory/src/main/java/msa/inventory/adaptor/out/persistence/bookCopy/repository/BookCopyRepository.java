package msa.inventory.adaptor.out.persistence.bookcopy.repository;

import msa.inventory.domain.model.BookCopy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
    Optional<BookCopy> findByCatalogId(Long catalogId);

    @Query(value = """
                    SELECT *
                    FROM book_copy
                    WHERE book_id = :bookId
                        AND status = :status
                    ORDER BY id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED 
            """, nativeQuery = true)
    Optional<BookCopy> lockFirstAvailableForUpdate(
            @Param("bookId") Long bookId,
            @Param("status") String status
    );
}
