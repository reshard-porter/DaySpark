package com.dayspark.app.ui;

import android.content.Context;
import android.text.format.DateFormat;

import com.dayspark.app.R;
import com.dayspark.app.data.DayClock;
import com.dayspark.app.data.Habit;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Localized text for dates, schedules and greetings. */
final class Formatters {

    private Formatters() {}

    static Locale locale(Context c) {
        return c.getResources().getConfiguration().getLocales().get(0);
    }

    static String longDate(Context c, long epochDay) {
        Locale locale = locale(c);
        String pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        return DateTimeFormatter.ofPattern(pattern, locale).format(DayClock.toDate(epochDay));
    }

    static String greeting(Context c) {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return c.getString(R.string.greeting_morning);
        if (hour < 17) return c.getString(R.string.greeting_afternoon);
        return c.getString(R.string.greeting_evening);
    }

    static String schedule(Context c, int mask) {
        if (mask == Habit.EVERY_DAY) return c.getString(R.string.schedule_every_day);
        if (mask == Habit.WEEKDAYS) return c.getString(R.string.schedule_weekdays);
        if (mask == 0b1100000) return c.getString(R.string.schedule_weekends);
        Locale locale = locale(c);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                names.add(DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, locale));
            }
        }
        return String.join(", ", names);
    }

    static String shortDayName(Context c, int index, TextStyle style) {
        return DayOfWeek.of(index + 1).getDisplayName(style, locale(c));
    }

    static String dayForAccessibility(Context c, LocalDate date) {
        Locale locale = locale(c);
        String pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        return DateTimeFormatter.ofPattern(pattern, locale).format(date);
    }
}
