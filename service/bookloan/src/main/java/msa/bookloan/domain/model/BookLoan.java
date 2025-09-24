package msa.bookloan.domain.model;

import jakarta.persistence.*;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;
import msa.common.domain.model.BookCategory;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookLoan extends BaseTimeEntity {

    @Id
    private Long id;

    private Long memberId;

    private Long bookId;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private LoanStatus loanStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private BookCategory bookCategory;

    private LocalDate loanDate;
    private LocalDate dueDate;
    private LocalDate returnDate;

    @Version
    private Long version;

    @Builder
    public BookLoan(Long id, Long memberId, Long bookId, LoanStatus loanStatus, LocalDate loanDate,
                    LocalDate dueDate, LocalDate returnDate, BookCategory bookCategory) {
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
