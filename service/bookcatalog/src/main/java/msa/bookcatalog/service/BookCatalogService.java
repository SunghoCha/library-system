package msa.bookcatalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.bookcatalog.domain.model.BookCatalog;
import msa.bookcatalog.domain.model.BookCatalogEditor;
import msa.bookcatalog.repository.BookCatalogRepository;
import msa.bookcatalog.service.dto.CreateBookCommand;
import msa.bookcatalog.service.dto.UpdateBookCommand;
import msa.bookcatalog.service.exception.BookCatalogNotFoundException;
import msa.bookcatalog.service.exception.DuplicateBookException;
import msa.common.events.EventType;
import msa.common.snowflake.Snowflake;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        BookCatalog saved = bookCatalogRepository.save(command.toEntity());

        eventPublisher.publishEvent(command.toEvent(snowflake.nextId()));
        return saved.getId();
    }

    public Long updateBookCatalog(UpdateBookCommand command) {
        validateDuplicate(command.isbn13());
        BookCatalog bookCatalog = bookCatalogRepository.findById(command.bookCatalogId())
                .orElseThrow(() -> new BookCatalogNotFoundException(command.bookCatalogId()));
        bookCatalog.edit(createEditor(command, bookCatalog));

        eventPublisher.publishEvent(command.toEvent(snowflake.nextId()));
        return bookCatalog.getId();
    }

    private static BookCatalogEditor createEditor(UpdateBookCommand command, BookCatalog bookCatalog) {
        return bookCatalog.toEditorBuilder()
                .title(command.title())
                .author(command.author())
                .publisher(command.publisher())
                .coverImageUrl(command.coverImageUrl())
                .description(command.description())
                .categoryId(command.categoryId())
                .categoryName(command.categoryName())
                .build();
    }

    private void validateDuplicate(String isbn13) {
        if (bookCatalogRepository.existsByIsbn13(isbn13)) {
            throw new DuplicateBookException(isbn13);
        }
    }
}
