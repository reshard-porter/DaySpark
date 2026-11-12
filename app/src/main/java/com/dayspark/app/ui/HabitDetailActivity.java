package com.dayspark.app.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.dayspark.app.R;
import com.dayspark.app.data.DayClock;
import com.dayspark.app.data.Habit;
import com.dayspark.app.data.HabitRepository;
import com.dayspark.app.data.StreakCalculator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.time.LocalDate;
import java.time.format.TextStyle;

/** One habit: today's status, streaks, completion rate and a five-week history. */
public final class HabitDetailActivity extends BaseActivity {

    public static final String EXTRA_HABIT_ID = "habit_id";
    private static final int HISTORY_WEEKS = 5;

    private HabitRepository repo;
    private Habit habit;
    private long habitId;
    private View root;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_habit_detail);
        root = findViewById(R.id.root);
        applySystemInsets(root);
        repo = HabitRepository.get(this);
        habitId = getIntent().getLongExtra(EXTRA_HABIT_ID, 0);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setUpToolbar(toolbar, true);
        toolbar.setTitle(R.string.title_habit);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit) {
                startActivity(new Intent(this, EditHabitActivity.class)
                        .putExtra(EditHabitActivity.EXTRA_HABIT_ID, habitId));
                return true;
            }
            return false;
        });
        findViewById(R.id.detail_toggle).setOnClickListener(v -> toggleToday());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        repo.loadHabit(habitId, loaded -> {
            if (loaded == null) { // deleted from the edit screen
                finish();
                return;
            }
            habit = loaded;
            bind();
        });
    }

    private void toggleToday() {
        if (habit == null) return;
        long today = DayClock.today();
        boolean nowDone = !habit.isCompletedOn(today);
        habit.markLocally(today, nowDone);
        bind();
        repo.setCompleted(habit.id, today, nowDone, ok -> {
            if (ok == null || !ok) {
                habit.markLocally(today, !nowDone);
                bind();
                showMessage(root, getString(R.string.error_save_completion));
            }
        });
    }

    private void bind() {
        long today = DayClock.today();
        ((TextView) findViewById(R.id.detail_name)).setText(habit.name);
        TextView note = findViewById(R.id.detail_note);
        note.setText(habit.note);
        note.setVisibility(habit.note.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.detail_schedule)).setText(Formatters.schedule(this, habit.daysMask));

        MaterialButton toggle = findViewById(R.id.detail_toggle);
        boolean done = habit.isCompletedOn(today);
        toggle.setText(done ? R.string.action_completed_today : R.string.action_mark_today);
        int primary = MaterialColors.getColor(toggle, androidx.appcompat.R.attr.colorPrimary);
        int onPrimary = MaterialColors.getColor(toggle, com.google.android.material.R.attr.colorOnPrimary);
        int container = MaterialColors.getColor(toggle, com.google.android.material.R.attr.colorPrimaryContainer);
        int onContainer = MaterialColors.getColor(toggle, com.google.android.material.R.attr.colorOnPrimaryContainer);
        toggle.setBackgroundTintList(ColorStateList.valueOf(done ? container : primary));
        toggle.setTextColor(done ? onContainer : onPrimary);
        toggle.setIconTint(ColorStateList.valueOf(done ? onContainer : onPrimary));
        ViewCompat.setStateDescription(toggle, getString(done
                ? R.string.state_completed : R.string.state_not_completed));

        bindStats(today);
        bindHistory(today);
        tintLegend();
    }

    private void bindStats(long today) {
        GridLayout grid = findViewById(R.id.stats_grid);
        grid.removeAllViews();
        int current = habit.currentStreak(today);
        int best = StreakCalculator.bestStreak(habit.completedDays(), habit.daysMask);
        int rate = StreakCalculator.completionPercent(habit.completedDays(), habit.daysMask,
                habit.createdDay, today, 30);
        int total = habit.completedDays().size();
        addStat(grid, getResources().getQuantityString(R.plurals.stat_days, current, current),
                getString(R.string.stat_current_streak));
        addStat(grid, getResources().getQuantityString(R.plurals.stat_days, best, best),
                getString(R.string.stat_best_streak));
        addStat(grid, getString(R.string.stat_percent, rate), getString(R.string.stat_30_day_rate));
        addStat(grid, String.valueOf(total), getString(R.string.stat_total));
    }

    private void addStat(GridLayout grid, String value, String label) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_stat, grid, false);
        ((TextView) card.findViewById(R.id.stat_value)).setText(value);
        ((TextView) card.findViewById(R.id.stat_label)).setText(label);
        card.setContentDescription(label + ": " + value);
        int gap = getResources().getDimensionPixelSize(R.dimen.space_s) / 2;
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(gap, gap, gap, gap);
        grid.addView(card, lp);
    }

    private void bindHistory(long today) {
        GridLayout grid = findViewById(R.id.history_grid);
        grid.removeAllViews();
        int cell = getResources().getDimensionPixelSize(R.dimen.history_cell);
        int primary = MaterialColors.getColor(grid, androidx.appcompat.R.attr.colorPrimary);
        int onPrimary = MaterialColors.getColor(grid, com.google.android.material.R.attr.colorOnPrimary);
        int missed = MaterialColors.getColor(grid, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int text = MaterialColors.getColor(grid, com.google.android.material.R.attr.colorOnSurface);
        int muted = MaterialColors.getColor(grid, com.google.android.material.R.attr.colorOnSurfaceVariant);

        for (int i = 0; i < 7; i++) {
            TextView head = new TextView(this);
            head.setText(Formatters.shortDayName(this, i, TextStyle.NARROW));
            head.setTextAppearance(R.style.TextAppearance_DaySpark_Caption);
            head.setGravity(Gravity.CENTER);
            head.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            grid.addView(head, cellParams());
        }

        // Grid starts on the Monday HISTORY_WEEKS-1 weeks before this week's Monday.
        long start = today - DayClock.dayIndex(today) - 7L * (HISTORY_WEEKS - 1);
        for (long day = start; day < start + 7L * HISTORY_WEEKS; day++) {
            TextView c = new TextView(this);
            LocalDate date = DayClock.toDate(day);
            c.setText(String.valueOf(date.getDayOfMonth()));
            c.setGravity(Gravity.CENTER);
            c.setTextAppearance(R.style.TextAppearance_DaySpark_Caption);
            c.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_day_cell));
            boolean scheduled = habit.isScheduledOn(day);
            boolean doneDay = habit.isCompletedOn(day);
            int stateText;
            if (day > today) {
                c.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                c.setTextColor(muted);
                c.setAlpha(0.5f);
                stateText = R.string.history_future;
            } else if (doneDay) {
                c.setBackgroundTintList(ColorStateList.valueOf(primary));
                c.setTextColor(onPrimary);
                stateText = R.string.history_done;
            } else if (scheduled && day < today && day >= habit.createdDay) {
                c.setBackgroundTintList(ColorStateList.valueOf(missed));
                c.setTextColor(text);
                stateText = R.string.history_missed;
            } else {
                c.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                c.setTextColor(muted);
                stateText = day == today ? R.string.history_open : R.string.history_rest;
            }
            c.setContentDescription(getString(R.string.cd_history_day,
                    Formatters.dayForAccessibility(this, date), getString(stateText)));
            if (day == today) c.setTypeface(c.getTypeface(), android.graphics.Typeface.BOLD);
            c.setMinHeight(cell); // grows with large font sizes instead of clipping
            grid.addView(c, cellParams());
        }
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        int m = getResources().getDimensionPixelSize(R.dimen.space_xs) / 2;
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private void tintLegend() {
        View done = findViewById(R.id.legend_done);
        View missed = findViewById(R.id.legend_missed);
        done.setBackgroundTintList(ColorStateList.valueOf(
                MaterialColors.getColor(done, androidx.appcompat.R.attr.colorPrimary)));
        missed.setBackgroundTintList(ColorStateList.valueOf(
                MaterialColors.getColor(missed, com.google.android.material.R.attr.colorSurfaceContainerHigh)));
    }
}
