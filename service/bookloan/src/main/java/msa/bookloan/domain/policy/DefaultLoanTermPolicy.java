package msa.bookloan.domain.policy;

import msa.common.domain.model.BookCategory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class DefaultLoanTermPolicy implements LoanTermPolicy {

    private static final int DEFAULT_LOAN_TERM = 7;
    private static final Map<BookCategory, Duration> TERMS = Map.of(
        BookCategory.NEW_RELEASE, Duration.ofDays(DEFAULT_LOAN_TERM),
        BookCategory.POPULAR, Duration.ofDays(10),
        BookCategory.STANDARD, Duration.ofDays(14)
    );

    @Override
    public Long loanPeriodFor(BookCategory category) {
        Duration duration = TERMS.getOrDefault(category, Duration.ofDays(DEFAULT_LOAN_TERM));
        return duration.toDays();
    }
}
