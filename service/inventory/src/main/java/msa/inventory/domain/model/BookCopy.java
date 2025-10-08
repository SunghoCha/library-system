package msa.inventory.domain.model;

import jakarta.persistence.*;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;

@Getter
@Setter
@Entity
@Table(name="book_copy")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCopy extends BaseTimeEntity {
    @Id
    private Long id;

    @Column(nullable=false)
    private Long catalogId;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private CopyStatus status = CopyStatus.AVAILABLE;

//    @Setter
//    @Version
//    private Long version;

    @Builder
    public BookCopy(Long id, Long catalogId, CopyStatus status) {
        this.id = id;
        this.catalogId = catalogId;
        this.status = status;
    }
}



