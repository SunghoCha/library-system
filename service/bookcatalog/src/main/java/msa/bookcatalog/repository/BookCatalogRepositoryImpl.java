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

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void bulkUpsert(List<BookCatalog> bookCatalogs) {
        String sql = "INSERT INTO book_catalog (id, title, author, pub_date, isbn13, publisher," +
                " cover_url, description, category_id, category_name, created_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "title = VALUES(title), " +
                "author = VALUES(author), " +
                "pub_date = VALUES(pub_date), " +
                "publisher = VALUES(publisher), " +
                "cover_url = VALUES(cover_url), " +
                "description = VALUES(description), " +
                "category_id = VALUES(category_id), " +
                "category_name = VALUES(category_name), " +
                "update_time = VALUES(update_time)";

        jdbcTemplate.batchUpdate(sql, bookCatalogs, 100, // 100개씩 묶어서 배치 처리
                (PreparedStatement ps, BookCatalog book) -> {
                    ps.setLong(1, book.getId());
                    ps.setString(2, book.getTitle());
                    ps.setString(3, book.getAuthor());
                    ps.setObject(4, book.getPublishDate());
                    ps.setString(5, book.getIsbn13());
                    ps.setString(6, book.getPublisher());
                    ps.setString(7, book.getCoverImageUrl());
                    ps.setString(8, book.getDescription());
                    ps.setObject(9, book.getCategoryId());
                    ps.setString(10, book.getCategoryName());
                    ps.setTimestamp(11, Timestamp.valueOf(book.getCreatedTime()));
                    ps.setTimestamp(12, Timestamp.valueOf(book.getUpdateTime()));
                });
    }
}
