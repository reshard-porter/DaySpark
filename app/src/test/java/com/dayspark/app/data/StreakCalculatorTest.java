package com.dayspark.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class StreakCalculatorTest {

    // 2026-11-09 is a Monday.
    private static final long MON = LocalDate.of(2026, 11, 9).toEpochDay();

    private static Set<Long> days(long... d) {
        Set<Long> s = new HashSet<>();
        for (long x : d) s.add(x);
        return s;
    }

    @Test
    public void dayIndexMatchesMask() {
        assertTrue(StreakCalculator.isScheduled(0b0000001, MON));        // Monday bit
        assertFalse(StreakCalculator.isScheduled(0b0000001, MON + 1));
        assertTrue(StreakCalculator.isScheduled(0b1000000, MON + 6));    // Sunday bit
        assertEquals(0, DayClock.dayIndex(MON));
        assertEquals(6, DayClock.dayIndex(MON + 6));
    }

    @Test
    public void todayOpenDoesNotBreakStreak() {
        long today = MON + 3;
        assertEquals(3, StreakCalculator.currentStreak(days(MON, MON + 1, MON + 2), Habit.EVERY_DAY, today));
        assertEquals(4, StreakCalculator.currentStreak(days(MON, MON + 1, MON + 2, today), Habit.EVERY_DAY, today));
    }

    @Test
    public void missedDayBreaksStreak() {
        long today = MON + 4;
        assertEquals(1, StreakCalculator.currentStreak(days(MON, MON + 1, MON + 3), Habit.EVERY_DAY, today));
    }

    @Test
    public void restDaysAreSkipped() {
        // Weekdays only: Fri done, Sat/Sun rest, Mon done -> streak 2 on Tuesday morning.
        long fri = MON - 3;
        assertEquals(2, StreakCalculator.currentStreak(days(fri, MON), Habit.WEEKDAYS, MON + 1));
    }

    @Test
    public void bestStreak() {
        assertEquals(3, StreakCalculator.bestStreak(days(MON, MON + 1, MON + 2, MON + 5, MON + 6), Habit.EVERY_DAY));
        assertEquals(0, StreakCalculator.bestStreak(days(), Habit.EVERY_DAY));
    }

    @Test
    public void completionPercentIgnoresOpenTodayAndPreCreation() {
        long today = MON + 4;
        // created Monday; Mon..Thu scheduled & elapsed = 4, done 3 -> 75%
        assertEquals(75, StreakCalculator.completionPercent(days(MON, MON + 1, MON + 3), Habit.EVERY_DAY, MON, today, 30));
        assertEquals(80, StreakCalculator.completionPercent(days(MON, MON + 1, MON + 3, today), Habit.EVERY_DAY, MON, today, 30));
        assertEquals(0, StreakCalculator.completionPercent(days(), Habit.EVERY_DAY, today, today, 30));
    }
}
