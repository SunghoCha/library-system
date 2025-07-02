package msa.bookloan.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Entity
@Getter
@RequiredArgsConstructor
public class BookLoan {

    @Id
    private Long id;

    private Long memberId;

    private LoanStatus loanStatus;

    @Builder
    public BookLoan(Long id, Long memberId, LoanStatus loanStatus) {
        this.id = id;
        this.memberId = memberId;
        this.loanStatus = loanStatus;
    }
}
