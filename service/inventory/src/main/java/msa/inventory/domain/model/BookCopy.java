package msa.inventory.domain.model;

import jakarta.persistence.*;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;

@Getter
@Setter
@Entity
@Table(name = "book_copy")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCopy extends BaseTimeEntity {
    @Id
    private Long id;

    @Column(nullable = false)
    private Long catalogId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookCopyStatus status = BookCopyStatus.AVAILABLE;

    @Column(name = "hold_saga_id")
    private Long holdSagaId;

    @Column(name = "current_loan_id")
    private Long currentLoanId;

    @Version
    private Long version;

    @Builder
    public BookCopy(Long id, Long catalogId, BookCopyStatus status) {
        this.id = id;
        this.catalogId = catalogId;
        this.status = status;
    }

    public boolean isAvailable() {
        return this.status == BookCopyStatus.AVAILABLE;
    }
}



