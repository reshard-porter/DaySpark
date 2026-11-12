package com.dayspark.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite schema owner.
 *
 * Data-preservation rule: migrations in {@link #onUpgrade} are additive only (new tables,
 * new columns with defaults). Never drop or recreate a table that holds user data.
 */
final class HabitDbHelper extends SQLiteOpenHelper {

    static final String DB_NAME = "dayspark.db";
    static final int DB_VERSION = 1;

    static final String T_HABITS = "habits";
    static final String T_COMPLETIONS = "completions";

    HabitDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_HABITS + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "note TEXT NOT NULL DEFAULT '',"
                + "days_mask INTEGER NOT NULL DEFAULT 127,"
                + "created_day INTEGER NOT NULL,"
                + "sort_order INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE " + T_COMPLETIONS + " ("
                + "habit_id INTEGER NOT NULL REFERENCES " + T_HABITS + "(_id) ON DELETE CASCADE,"
                + "day INTEGER NOT NULL,"
                + "PRIMARY KEY (habit_id, day))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Example for a future version 2:
        // if (oldVersion < 2) db.execSQL("ALTER TABLE habits ADD COLUMN color INTEGER NOT NULL DEFAULT 0");
    }
}
