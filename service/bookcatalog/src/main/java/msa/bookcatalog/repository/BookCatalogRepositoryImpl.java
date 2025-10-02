package msa.bookcatalog.repository;

import lombok.RequiredArgsConstructor;
import msa.bookcatalog.domain.model.BookCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookCatalogRepositoryImpl implements BookCatalogRepositoryCustom {
}
