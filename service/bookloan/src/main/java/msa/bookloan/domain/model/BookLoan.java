package msa.bookloan.domain.model;

import jakarta.persistence.*;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
    @Column(length = 24) // (null대신 PENDING)
    private LoanStatus loanStatus;

    @Column
    private LocalDate loanDate;

    @Column
    private LocalDate dueDate;

    @Column
    private LocalDate returnDate;

    @Version
    private Long version;

    @Column(name = "current_saga_id") // 일종의 시맨틱락으로 사용 
    private Long currentSagaId;

    public static BookLoan createNew(Long id, Long memberId, Long bookId, Long sagaId) {
        return BookLoan.builder()
                .id(id)
                .memberId(memberId)
                .bookId(bookId)
                .loanStatus(LoanStatus.PENDING)
                .loanDate(null)
                .dueDate(null)
                .returnDate(null)
                .currentSagaId(sagaId)
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
