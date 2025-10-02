package msa.bookcatalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.domain.model.BookCategory;
import msa.bookcatalog.domain.model.BookType;
import msa.bookcatalog.repository.BookCatalogRepository;
import msa.bookcatalog.service.dto.CreateBookCommand;
import msa.bookcatalog.service.dto.UpdateBookCommand;
import msa.bookcatalog.service.exception.BookCatalogNotFoundException;
import msa.bookcatalog.service.exception.DuplicateBookException;
import msa.common.domain.model.BookTypeRef;
import msa.common.domain.model.CategoryRef;
import msa.common.events.EventType;
import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import msa.common.snowflake.Snowflake;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BookCatalogService {

    private final BookCatalogRepository bookCatalogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Snowflake snowflake;

    public Long createBookCatalog(CreateBookCommand command) {
        validateDuplicate(command.isbn13());
        long id = snowflake.nextId();
        BookCatalog bookCatalog = BookCatalog.builder()
                .id(id)
                .isbn13(command.isbn13())
                .title(command.title())
                .author(command.author())
                .publisher(command.publisher())
                .description(command.description())
                .coverImageUrl(command.coverImageUrl())
                .publishDate(command.publishDate())
                .category(BookCategory.fromId(command.categoryId()))
                .bookType(BookType.valueOf(command.bookType()))
                .build();

        BookCatalog saved = bookCatalogRepository.save(bookCatalog);

        eventPublisher.publishEvent(toEventFrom(saved, EventType.CREATED));
        return saved.getId();
    }

    public Long updateBookCatalog(UpdateBookCommand command) {
        BookCatalog bookCatalog = bookCatalogRepository.findById(command.bookCatalogId())
                .orElseThrow(() -> new BookCatalogNotFoundException(command.bookCatalogId()));

        BookCatalog.BookCatalogEditor editor = buildPatchEditor(bookCatalog, command);
        boolean changed = bookCatalog.applyEditor(editor);
        if (!changed) {
            log.debug("변경 없음 — 이벤트/업데이트 스킵. id={}", bookCatalog.getId());
            return bookCatalog.getId();
        }
        bookCatalogRepository.flush(); // 버전 정보 업데이트

        eventPublisher.publishEvent(toEventFrom(bookCatalog, EventType.UPDATED));
        return bookCatalog.getId();
    }


    private void validateDuplicate(String isbn13) {
        if (bookCatalogRepository.existsByIsbn13(isbn13)) {
            throw new DuplicateBookException(isbn13);
        }
    }

    private BookCatalog.BookCatalogEditor buildPatchEditor(BookCatalog existing, UpdateBookCommand command) {
        return existing.toEditorBuilder()
                .title(command.title())
                .author(command.author())
                .publisher(command.publisher())
                .coverImageUrl(command.coverImageUrl())
                .description(command.description())
                .publishDate(command.publishDate())
                .bookType(command.bookType() != null ? BookType.valueOf(command.bookType()) : null)
                .build();
    }

    private BookCatalogChangedEvent toEventFrom(BookCatalog bookCatalog, EventType eventType) {
        CategoryRef categoryRef = new CategoryRef(bookCatalog.getCategory().categoryId(), bookCatalog.getCategory().categoryName());
        BookTypeRef bookTypeRef = new BookTypeRef(bookCatalog.getBookType().name(), bookCatalog.getBookType().displayName());

        return BookCatalogChangedEvent.builder()
                .eventId(snowflake.nextId())
                .eventType(eventType)
                .bookId(bookCatalog.getId())
                .aggregateVersion(bookCatalog.getVersion())
                .title(bookCatalog.getTitle())
                .author(bookCatalog.getAuthor())
                .category(categoryRef)
                .bookType(bookTypeRef)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private BookCatalog buildNewBook(CreateBookCommand command) {
        long id = snowflake.nextId();

        return BookCatalog.builder()
                .id(id)
                .isbn13(command.isbn13())
                .title(command.title())
                .author(command.author())
                .publisher(command.publisher())
                .description(command.description())
                .coverImageUrl(command.coverImageUrl())
                .publishDate(command.publishDate())
                .category(BookCategory.fromId(command.categoryId()))
                .bookType(resolveInitialBookType(command.bookType(), command.publishDate()))
                .build();
    }

    private static BookType resolveInitialBookType(String code, LocalDate pubDate) {
        if (code != null && !code.isBlank()) {
            try { return BookType.valueOf(code.trim().toUpperCase()); }
            catch (IllegalArgumentException ignore) { /* fall through */ }
        }
        // 기본 정책: 출간 30일 이내면 신간, 아니면 일반
        if (pubDate != null && pubDate.isAfter(LocalDate.now().minusDays(30))) {
            return BookType.NEW_RELEASE;
        }
        return BookType.STANDARD;
    }
}
