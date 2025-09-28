package msa.bookloan.domain.policy;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class DefaultLoanTermPolicy implements LoanTermPolicy {

    private static final int DEFAULT_LOAN_TERM = 7;
    private static final Map<BookType, Duration> TERMS = Map.of(
        BookType.NEW_RELEASE, Duration.ofDays(DEFAULT_LOAN_TERM),
        BookType.POPULAR, Duration.ofDays(10),
        BookType.STANDARD, Duration.ofDays(14)
    );

    @Override
    public Long loanPeriodFor(BookType category) {
        Duration duration = TERMS.getOrDefault(category, Duration.ofDays(DEFAULT_LOAN_TERM));
        return duration.toDays();
    }
}
