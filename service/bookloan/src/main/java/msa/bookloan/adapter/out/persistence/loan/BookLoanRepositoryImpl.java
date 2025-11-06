package msa.bookloan.adapter.out.persistence.loan;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import msa.bookloan.domain.model.LoanStatus;
import msa.bookloan.domain.model.QBookLoan;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class BookLoanRepositoryImpl implements BookLoanRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QBookLoan bookLoan = QBookLoan.bookLoan;

    @Override
    public long countOverdue(Long memberId, LocalDate today) {
        Long count = queryFactory
                .select(bookLoan.count())
                .from(bookLoan)
                .where(
                        bookLoan.memberId.eq(memberId),
                        bookLoan.loanStatus.eq(LoanStatus.LOANED),
                        bookLoan.returnDate.isNull(),
                        bookLoan.dueDate.lt(today)
                )
                .fetchOne();
        return count != null ? count : 0L;
    }

    @Override
    public long countActiveByMember(Long memberId) {
        Long count = queryFactory
                .select(bookLoan.count())
                .from(bookLoan)
                .where(
                        bookLoan.memberId.eq(memberId),
                        bookLoan.active.isTrue()
                )
                .fetchOne();

        return count != null ? count : 0L;
    }
}
