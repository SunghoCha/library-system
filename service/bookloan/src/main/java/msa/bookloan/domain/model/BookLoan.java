package msa.bookloan.domain.model;

import jakarta.persistence.*;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

@Entity
@Table(
        name = "book_loan",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_member_book_active",
                columnNames = {"member_id", "book_id", "active"}
        ),
        indexes = { // TODO : 인덱스 점검하기
        @Index(name="idx_book_loan_member_active", columnList="member_id,active"),
        @Index(name="idx_book_loan_overdue", columnList="member_id,loan_status,return_date,due_date")
}
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookLoan extends BaseTimeEntity implements Persistable<Long> {

    @Id
    @Column(name = "book_loan_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "book_id", nullable = false)
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

    @Column
    private Boolean active; // 부분 유니크 (true or null)

    public static BookLoan createPending(CreateSpec spec) {
        return BookLoan.builder()
                .id(spec.loanId())
                .memberId(spec.memberId())
                .bookId(spec.bookId())
                .currentSagaId(spec.sagaId())
                .loanStatus(LoanStatus.PENDING)
                .loanDate(spec.loanDate())
                .dueDate(spec.dueDate())
                .returnDate(null)
                .active(true)
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

    public void markLoaned() {
        this.loanStatus = LoanStatus.LOANED;
    }

    public static record CreateSpec(
            Long loanId, Long memberId, Long bookId, Long sagaId,
            LocalDate loanDate, LocalDate dueDate
    ) { }

}
