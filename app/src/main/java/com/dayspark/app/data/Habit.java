package com.dayspark.app.data;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A habit and the days it was completed. Days are local "epoch days"
 * (days since 1970-01-01 in the device time zone), see {@link DayClock}.
 */
public final class Habit {

    /** Bit 0 = Monday ... bit 6 = Sunday. */
    public static final int EVERY_DAY = 0b1111111;
    public static final int WEEKDAYS = 0b0011111;
    public static final int MAX_NAME_LENGTH = 40;

    public long id;
    public String name = "";
    public String note = "";
    public int daysMask = EVERY_DAY;
    public long createdDay;
    public int sortOrder;

    private final TreeSet<Long> completedDays = new TreeSet<>();

    public Set<Long> completedDays() {
        return Collections.unmodifiableSet(completedDays);
    }

    public boolean isCompletedOn(long day) {
        return completedDays.contains(day);
    }

    void setCompletedDays(Set<Long> days) {
        completedDays.clear();
        completedDays.addAll(days);
    }

    /** Local, optimistic update used by the UI while the database write runs. */
    public void markLocally(long day, boolean done) {
        if (done) completedDays.add(day); else completedDays.remove(day);
    }

    public boolean isScheduledOn(long day) {
        return StreakCalculator.isScheduled(daysMask, day);
    }

    public int currentStreak(long today) {
        return StreakCalculator.currentStreak(completedDays, daysMask, today);
    }
}
