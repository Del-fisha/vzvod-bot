package com.company.shtopor.vzvodbot.shift;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Зеркало {@code DepartmentConverter.departmentFromDate} из Core: отделение на дату смены.
 */
public final class DepartmentFromDate {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 2, 22);

    private DepartmentFromDate() {
    }

    public static int toInt(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        long daysBetween = ChronoUnit.DAYS.between(ANCHOR, date);
        int normalized = (int) ((daysBetween % 4 + 4) % 4);
        return switch (normalized) {
            case 0, 1 -> 1;
            default -> 2;
        };
    }
}
