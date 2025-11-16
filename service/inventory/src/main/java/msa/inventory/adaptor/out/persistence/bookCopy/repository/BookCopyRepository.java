package msa.inventory.adaptor.out.persistence.bookCopy.repository;

import msa.inventory.domain.model.BookCopy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
}
