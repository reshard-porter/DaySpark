package com.dayspark.app.data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

/** Converts between wall-clock dates and the local epoch-day numbers stored in the database. */
public final class DayClock {

    private DayClock() {}

    public static long today() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    public static LocalDate toDate(long epochDay) {
        return LocalDate.ofEpochDay(epochDay);
    }

    /** Monday = 0 ... Sunday = 6, matching the bit order of {@link Habit#daysMask}. */
    public static int dayIndex(long epochDay) {
        DayOfWeek dow = LocalDate.ofEpochDay(epochDay).getDayOfWeek();
        return dow.getValue() - 1;
    }
}
