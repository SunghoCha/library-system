package msa.bookcatalog.infra.aladin;

import msa.bookcatalog.infra.aladin.model.QueryDate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QueryDateGenerator {

    public static List<QueryDate> generateWeeklyQueryDates(YearMonth start, YearMonth end) {
        ArrayList<QueryDate> result = new ArrayList<>();
        WeekFields weekFields = WeekFields.of(Locale.KOREA);
        for (YearMonth ym = start; ym.isBefore(end); ym = ym.plusMonths(1)) {
            int lastWeek = ym.atEndOfMonth().get(weekFields.weekOfMonth());
            for (int w = 1; w <= lastWeek; w++) {
                result.add(new QueryDate(ym.getYear(), ym.getMonthValue(), w));
            }
        }
        return result;
    }

}
