package msa.bookloan.domain.model;

import jakarta.persistence.*;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.Objects;

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
@Builder // TODO: 마무리단게때 생성자 빌더로 옮길지 고민
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookLoan extends BaseTimeEntity {

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
    private Boolean active; // 부분 유니크 (true or null, null은 유니크 중복가능) 중복대출 방어

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

    public void markLoaned() {
        this.loanStatus = LoanStatus.LOANED;
        this.active = true;
    }

    public void markReturned(LocalDate returnDate) {
        this.loanStatus = LoanStatus.RETURNED;
        this.returnDate = returnDate;
        this.active = null; // 더 이상 활성 대출 아님
    }

    public void markCancelled() {
        this.loanStatus = LoanStatus.CANCELLED;
        this.active = null;
    }

    public void markFailed() {
        this.loanStatus = LoanStatus.FAILED;
        this.active = null;
    }

    public void attachSaga(Long sagaId) {
        Objects.requireNonNull(sagaId, "sagaId must not be null");

        if (currentSagaId != null && !currentSagaId.equals(sagaId)) {
            throw new IllegalStateException(
                    "이미 다른 사가가 진행 중입니다. current=" + this.currentSagaId + ", requested=" + sagaId
            );
        }
        this.currentSagaId = sagaId;
    }

    public boolean isCancellableBy(Long memberId) {
        return this.memberId.equals(memberId)
                && Boolean.TRUE.equals(this.active)
                && this.loanStatus == LoanStatus.PENDING;
    }

    public static record CreateSpec(
            Long loanId, Long memberId, Long bookId, Long sagaId,
            LocalDate loanDate, LocalDate dueDate
    ) { }

}
