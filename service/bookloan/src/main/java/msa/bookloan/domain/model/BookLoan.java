package msa.bookloan.domain.model;

import jakarta.persistence.*;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookLoan extends BaseTimeEntity implements Persistable<Long> {

    @Id
    @Column(name = "book_loan_id")
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long bookId;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private LoanProcessStatus processStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 24) // NULL 허용 (LOANED 때 채움)
    private LoanStatus loanStatus;

    @Column(nullable = false)
    private LocalDate loanDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column
    private LocalDate returnDate;

    @Version
    private Long version;

    @Column(name = "current_saga_id") // 일종의 시맨틱락으로 사용 
    private Long currentSagaId;

    @Builder
    public BookLoan(Long id, Long memberId, Long bookId, LoanStatus loanStatus, LocalDate loanDate,
                    LoanProcessStatus processStatus, LocalDate dueDate, LocalDate returnDate) {
        this.id = id;
        this.memberId = memberId;
        this.bookId = bookId;
        this.loanStatus = loanStatus;
        this.processStatus = processStatus;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
    }

    public static BookLoan createNew(Long id, Long memberId, Long bookId) {
        return BookLoan.builder()
                .id(id)
                .memberId(memberId)
                .bookId(bookId)
                .processStatus(LoanProcessStatus.RECEIVED)
                .loanStatus(null)
                .loanDate(null)
                .dueDate(null)
                .returnDate(null)
                .build();
    }




    // Persistable 구현

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public Long getId() {
        return id;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

}
