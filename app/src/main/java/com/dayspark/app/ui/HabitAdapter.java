package com.dayspark.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.dayspark.app.R;
import com.dayspark.app.data.Habit;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** Dashboard list: a header, then either habit cards or the empty state. */
final class HabitAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Listener {
        void onOpenHabit(Habit habit);

        void onToggleHabit(Habit habit);

        void onCreateHabit();
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_HABIT = 1;
    private static final int TYPE_EMPTY = 2;

    private final Listener listener;
    private final List<Habit> habits = new ArrayList<>();
    private long today;
    private boolean loaded;

    HabitAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<Habit> items, long today) {
        habits.clear();
        habits.addAll(items);
        this.today = today;
        loaded = true;
        notifyDataSetChanged();
    }

    void refreshHabit(Habit habit) {
        int index = habits.indexOf(habit);
        if (index >= 0) notifyItemChanged(index + 1);
        notifyItemChanged(0);
    }

    @Override
    public int getItemCount() {
        if (!loaded) return 1;
        return 1 + (habits.isEmpty() ? 1 : habits.size());
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return TYPE_HEADER;
        return habits.isEmpty() ? TYPE_EMPTY : TYPE_HABIT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inf.inflate(R.layout.item_dashboard_header, parent, false));
        }
        if (viewType == TYPE_EMPTY) {
            View v = inf.inflate(R.layout.view_empty_state, parent, false);
            v.findViewById(R.id.empty_create).setOnClickListener(x -> listener.onCreateHabit());
            return new RecyclerView.ViewHolder(v) {};
        }
        return new HabitHolder(inf.inflate(R.layout.item_habit, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind();
        } else if (holder instanceof HabitHolder) {
            ((HabitHolder) holder).bind(habits.get(position - 1));
        }
    }

    final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView date = itemView.findViewById(R.id.header_date);
        final TextView greeting = itemView.findViewById(R.id.header_greeting);
        final MaterialCardView card = itemView.findViewById(R.id.progress_card);
        final TextView summary = itemView.findViewById(R.id.header_summary);
        final LinearProgressIndicator progress = itemView.findViewById(R.id.header_progress);
        final TextView section = itemView.findViewById(R.id.header_section);

        HeaderHolder(View v) {
            super(v);
        }

        void bind() {
            Context c = itemView.getContext();
            date.setText(Formatters.longDate(c, today));
            greeting.setText(Formatters.greeting(c));
            boolean show = loaded && !habits.isEmpty();
            card.setVisibility(show ? View.VISIBLE : View.GONE);
            section.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!show) return;
            int scheduled = 0;
            int done = 0;
            for (Habit h : habits) {
                if (h.isScheduledOn(today)) {
                    scheduled++;
                    if (h.isCompletedOn(today)) done++;
                }
            }
            if (scheduled == 0) {
                summary.setText(R.string.summary_rest_day);
                progress.setProgressCompat(0, true);
            } else if (done == scheduled) {
                summary.setText(R.string.summary_all_done);
                progress.setProgressCompat(100, true);
            } else {
                summary.setText(c.getString(R.string.summary_progress, done, scheduled));
                progress.setProgressCompat(Math.round(done * 100f / scheduled), true);
            }
            progress.setContentDescription(summary.getText());
        }
    }

    final class HabitHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card = itemView.findViewById(R.id.habit_card);
        final TextView name = itemView.findViewById(R.id.habit_name);
        final TextView schedule = itemView.findViewById(R.id.habit_schedule);
        final TextView streak = itemView.findViewById(R.id.habit_streak);
        final LinearLayout week = itemView.findViewById(R.id.habit_week_strip);
        final MaterialButton check = itemView.findViewById(R.id.habit_check);

        HabitHolder(View v) {
            super(v);
        }

        void bind(Habit h) {
            Context c = itemView.getContext();
            boolean done = h.isCompletedOn(today);
            boolean scheduledToday = h.isScheduledOn(today);
            int streakDays = h.currentStreak(today);

            name.setText(h.name);
            schedule.setText(scheduledToday
                    ? Formatters.schedule(c, h.daysMask)
                    : c.getString(R.string.schedule_rest_today, Formatters.schedule(c, h.daysMask)));
            streak.setText(streakDays == 0
                    ? c.getString(R.string.streak_none)
                    : c.getResources().getQuantityString(R.plurals.streak_days, streakDays, streakDays));
            bindWeek(h);
            WeekStrip.bindCheck(check, done);

            check.setContentDescription(c.getString(done
                    ? R.string.cd_mark_incomplete : R.string.cd_mark_complete, h.name));
            ViewCompat.setStateDescription(check, c.getString(done
                    ? R.string.state_completed : R.string.state_not_completed));
            check.setOnClickListener(v -> listener.onToggleHabit(h));

            card.setContentDescription(c.getString(R.string.cd_habit_card, h.name,
                    schedule.getText(), streak.getText()));
            card.setOnClickListener(v -> listener.onOpenHabit(h));
        }

        private void bindWeek(Habit h) {
            WeekStrip.bind(week, h, today);
        }
    }

    /** Small helpers shared with the theme preview's sample cards. */
    static final class WeekStrip {
        private WeekStrip() {}

        static void bind(LinearLayout strip, Habit h, long today) {
            Context c = strip.getContext();
            int size = c.getResources().getDimensionPixelSize(R.dimen.day_dot);
            int gap = c.getResources().getDimensionPixelSize(R.dimen.space_xs);
            if (strip.getChildCount() != 7) {
                strip.removeAllViews();
                for (int i = 0; i < 7; i++) {
                    View dot = new View(c);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.setMarginEnd(gap);
                    dot.setLayoutParams(lp);
                    dot.setBackgroundResource(R.drawable.bg_dot);
                    strip.addView(dot);
                }
            }
            int doneColor = MaterialColors.getColor(strip, androidx.appcompat.R.attr.colorPrimary);
            int missColor = MaterialColors.getColor(strip, com.google.android.material.R.attr.colorOutlineVariant);
            int restColor = MaterialColors.getColor(strip, com.google.android.material.R.attr.colorSurfaceContainerHigh);
            for (int i = 0; i < 7; i++) {
                long day = today - 6 + i;
                int color = h.isCompletedOn(day) ? doneColor
                        : (h.isScheduledOn(day) ? missColor : restColor);
                strip.getChildAt(i).setBackgroundTintList(ColorStateList.valueOf(color));
            }
        }

        static void bindCheck(MaterialButton check, boolean done) {
            int primary = MaterialColors.getColor(check, androidx.appcompat.R.attr.colorPrimary);
            int onPrimary = MaterialColors.getColor(check, com.google.android.material.R.attr.colorOnPrimary);
            int outline = MaterialColors.getColor(check, com.google.android.material.R.attr.colorOutline);
            check.setBackgroundTintList(ColorStateList.valueOf(done ? primary : android.graphics.Color.TRANSPARENT));
            check.setIconTint(ColorStateList.valueOf(done ? onPrimary : outline));
            check.setStrokeColor(ColorStateList.valueOf(done ? primary : outline));
        }
    }
}
