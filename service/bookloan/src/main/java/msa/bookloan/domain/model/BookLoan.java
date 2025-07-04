package msa.bookloan.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import msa.common.domain.BaseTimeEntity;
import msa.common.domain.BookCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@RequiredArgsConstructor
public class BookLoan extends BaseTimeEntity {

    @Id
    private Long id;

    private Long memberId;

    private Long bookId;

    private LoanStatus loanStatus;

    private BookCategory bookCategory;

    private LocalDateTime loanDate;

    private LocalDateTime dueDate;

    private LocalDateTime returnDate;


    @Builder
    public BookLoan(Long id, Long memberId, Long bookId, LoanStatus loanStatus, LocalDateTime loanDate,
                    LocalDateTime dueDate, LocalDateTime returnDate, BookCategory bookCategory) {
        this.id = id;
        this.memberId = memberId;
        this.bookId = bookId;
        this.loanStatus = loanStatus;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.bookCategory = bookCategory;
    }
}
