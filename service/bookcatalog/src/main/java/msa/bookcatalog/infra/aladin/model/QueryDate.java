package msa.bookcatalog.infra.aladin.model;


import java.time.LocalDate;
import java.time.temporal.WeekFields;

public record QueryDate(Integer year, Integer month, Integer week) {

    public static QueryDate defaultDate() {
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        int currentWeek = now.get(WeekFields.ISO.weekOfMonth()) - 1;

        return new QueryDate(currentYear, currentMonth, currentWeek);
    }
}
