package com.dayspark.app.data;

import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure streak and progress maths. No Android dependencies, so it is unit tested directly.
 *
 * Rules:
 * - Only scheduled days count. Rest days neither extend nor break a streak.
 * - Today does not break the current streak until the day is over.
 */
public final class StreakCalculator {

    private StreakCalculator() {}

    public static boolean isScheduled(int daysMask, long day) {
        int index = Math.floorMod(day + 3, 7); // 1970-01-01 was a Thursday (index 3)
        return (daysMask & (1 << index)) != 0;
    }

    public static int currentStreak(Set<Long> done, int daysMask, long today) {
        if (done.isEmpty() || daysMask == 0) return 0;
        NavigableSet<Long> days = asNavigable(done);
        long earliest = days.first();
        int streak = 0;
        long day = today;
        if (isScheduled(daysMask, today) && !days.contains(today)) {
            day = today - 1; // today is still open
        }
        for (; day >= earliest; day--) {
            if (!isScheduled(daysMask, day)) continue;
            if (days.contains(day)) streak++;
            else break;
        }
        return streak;
    }

    public static int bestStreak(Set<Long> done, int daysMask) {
        if (done.isEmpty() || daysMask == 0) return 0;
        NavigableSet<Long> days = asNavigable(done);
        int best = 0;
        int run = 0;
        for (long day = days.first(); day <= days.last(); day++) {
            if (!isScheduled(daysMask, day)) continue;
            if (days.contains(day)) {
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
        }
        return best;
    }

    /**
     * Share of scheduled days completed within the last {@code windowDays} days, never counting
     * days before the habit existed. Returns a value between 0 and 100.
     */
    public static int completionPercent(Set<Long> done, int daysMask, long createdDay,
                                        long today, int windowDays) {
        long start = Math.max(createdDay, today - windowDays + 1);
        int scheduled = 0;
        int completed = 0;
        for (long day = start; day <= today; day++) {
            if (!isScheduled(daysMask, day)) continue;
            if (day == today && !done.contains(day)) continue; // today still open
            scheduled++;
            if (done.contains(day)) completed++;
        }
        if (scheduled == 0) return 0;
        return Math.round(completed * 100f / scheduled);
    }

    private static NavigableSet<Long> asNavigable(Set<Long> set) {
        return set instanceof NavigableSet ? (NavigableSet<Long>) set : new TreeSet<>(set);
    }
}
