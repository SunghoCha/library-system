package msa.bookloan.domain.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import msa.common.domain.base.AbstractPersistableEntity;
import msa.common.domain.base.BaseTimeEntity;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookLoan extends BaseTimeEntity implements Persistable<Long> {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private LoanStatus loanStatus;

    @Column(nullable = false)
    private LocalDate loanDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column
    private LocalDate returnDate;

    @Version
    private Long version;

    @Builder
    public BookLoan(Long id, Long memberId, Long bookId, LoanStatus loanStatus, LocalDate loanDate,
                    LocalDate dueDate, LocalDate returnDate) {
        this.id = id;
        this.memberId = memberId;
        this.bookId = bookId;
        this.loanStatus = loanStatus;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
    }

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
