package msa.bookcatalog.domain.policy;

import msa.bookcatalog.domain.model.BookType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class NewReleaseBookTypePolicy implements BookTypePolicy {

    private final int newReleaseDays;

    public NewReleaseBookTypePolicy(@Value("${book.type.new-release-days:30}") int newReleaseDays) {
        this.newReleaseDays = newReleaseDays;
    }

    @Override
    public BookType decide(LocalDate publishDate, LocalDate today) {
        if (publishDate == null) return BookType.STANDARD;
        return publishDate.isAfter(today.minusDays(newReleaseDays))
                ? BookType.NEW_RELEASE
                : BookType.STANDARD;
    }


}
