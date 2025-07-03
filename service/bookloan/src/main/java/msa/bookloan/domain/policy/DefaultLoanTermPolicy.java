package msa.bookloan.domain.policy;

import msa.common.domain.BookCategory;
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
    public Duration loanPeriodFor(BookCategory category) {
        return TERMS.getOrDefault(category, Duration.ofDays(DEFAULT_LOAN_TERM));
    }
}
