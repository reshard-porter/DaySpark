package com.dayspark.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All habit reads and writes. Database work runs on one background thread (so writes are
 * ordered); results are delivered on the main thread.
 */
public final class HabitRepository {

    public interface Callback<T> {
        void onResult(@Nullable T value);
    }

    private static final String TAG = "HabitRepository";
    private static volatile HabitRepository instance;

    private final HabitDbHelper helper;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private HabitRepository(Context context) {
        helper = new HabitDbHelper(context);
    }

    public static HabitRepository get(Context context) {
        if (instance == null) {
            synchronized (HabitRepository.class) {
                if (instance == null) instance = new HabitRepository(context);
            }
        }
        return instance;
    }

    public void loadHabits(Callback<List<Habit>> callback) {
        run(() -> {
            SQLiteDatabase db = helper.getReadableDatabase();
            List<Habit> habits = new ArrayList<>();
            Map<Long, Habit> byId = new HashMap<>();
            try (Cursor c = db.query(HabitDbHelper.T_HABITS, null, null, null, null, null,
                    "sort_order ASC, _id ASC")) {
                while (c.moveToNext()) {
                    Habit h = readHabit(c);
                    habits.add(h);
                    byId.put(h.id, h);
                }
            }
            Map<Long, Set<Long>> days = new HashMap<>();
            try (Cursor c = db.query(HabitDbHelper.T_COMPLETIONS, new String[]{"habit_id", "day"},
                    null, null, null, null, null)) {
                while (c.moveToNext()) {
                    days.computeIfAbsent(c.getLong(0), k -> new HashSet<>()).add(c.getLong(1));
                }
            }
            for (Map.Entry<Long, Set<Long>> e : days.entrySet()) {
                Habit h = byId.get(e.getKey());
                if (h != null) h.setCompletedDays(e.getValue());
            }
            return habits;
        }, callback);
    }

    public void loadHabit(long id, Callback<Habit> callback) {
        run(() -> {
            SQLiteDatabase db = helper.getReadableDatabase();
            Habit habit = null;
            try (Cursor c = db.query(HabitDbHelper.T_HABITS, null, "_id=?",
                    new String[]{String.valueOf(id)}, null, null, null)) {
                if (c.moveToFirst()) habit = readHabit(c);
            }
            if (habit == null) return null;
            Set<Long> done = new HashSet<>();
            try (Cursor c = db.query(HabitDbHelper.T_COMPLETIONS, new String[]{"day"}, "habit_id=?",
                    new String[]{String.valueOf(id)}, null, null, null)) {
                while (c.moveToNext()) done.add(c.getLong(0));
            }
            habit.setCompletedDays(done);
            return habit;
        }, callback);
    }

    /** Inserts when {@code habit.id == 0}, otherwise updates. Returns the habit id, or null on failure. */
    public void saveHabit(Habit habit, Callback<Long> callback) {
        final String name = habit.name.trim();
        final String note = habit.note.trim();
        final int mask = habit.daysMask;
        final long id = habit.id;
        final long created = habit.createdDay;
        run(() -> {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("name", name);
            v.put("note", note);
            v.put("days_mask", mask);
            if (id == 0) {
                v.put("created_day", created != 0 ? created : DayClock.today());
                long newId = db.insertOrThrow(HabitDbHelper.T_HABITS, null, v);
                v.clear();
                v.put("sort_order", newId);
                db.update(HabitDbHelper.T_HABITS, v, "_id=?", new String[]{String.valueOf(newId)});
                return newId;
            }
            db.update(HabitDbHelper.T_HABITS, v, "_id=?", new String[]{String.valueOf(id)});
            return id;
        }, callback);
    }

    public void deleteHabit(long id, Callback<Boolean> callback) {
        run(() -> helper.getWritableDatabase().delete(HabitDbHelper.T_HABITS, "_id=?",
                new String[]{String.valueOf(id)}) > 0, callback);
    }

    /** Idempotent: marking a day twice, or clearing an unmarked day, is harmless. */
    public void setCompleted(long habitId, long day, boolean done, Callback<Boolean> callback) {
        run(() -> {
            SQLiteDatabase db = helper.getWritableDatabase();
            if (done) {
                ContentValues v = new ContentValues();
                v.put("habit_id", habitId);
                v.put("day", day);
                db.insertWithOnConflict(HabitDbHelper.T_COMPLETIONS, null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
            } else {
                db.delete(HabitDbHelper.T_COMPLETIONS, "habit_id=? AND day=?",
                        new String[]{String.valueOf(habitId), String.valueOf(day)});
            }
            return true;
        }, callback);
    }

    private static Habit readHabit(Cursor c) {
        Habit h = new Habit();
        h.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        h.name = c.getString(c.getColumnIndexOrThrow("name"));
        h.note = c.getString(c.getColumnIndexOrThrow("note"));
        h.daysMask = c.getInt(c.getColumnIndexOrThrow("days_mask"));
        h.createdDay = c.getLong(c.getColumnIndexOrThrow("created_day"));
        h.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
        return h;
    }

    private interface Work<T> {
        T run();
    }

    private <T> void run(Work<T> work, @Nullable Callback<T> callback) {
        io.execute(() -> {
            T result;
            try {
                result = work.run();
            } catch (RuntimeException e) {
                Log.e(TAG, "Database operation failed", e);
                result = null;
            }
            final T value = result;
            if (callback != null) main.post(() -> callback.onResult(value));
        });
    }
}
