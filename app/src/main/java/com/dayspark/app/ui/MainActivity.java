package com.dayspark.app.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dayspark.app.R;
import com.dayspark.app.data.DayClock;
import com.dayspark.app.data.Habit;
import com.dayspark.app.data.HabitRepository;
import com.dayspark.app.theme.ThemePreferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

/** Dashboard: today's habits, progress, and one-tap completion. */
public final class MainActivity extends BaseActivity implements HabitAdapter.Listener {

    public static final String EXTRA_MESSAGE = "dayspark.message";

    private HabitRepository repo;
    private HabitAdapter adapter;
    private View root;
    private boolean loading = true;

    private final ActivityResultLauncher<Intent> editLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    int msg = result.getData().getIntExtra(EXTRA_MESSAGE, 0);
                    if (msg != 0) showMessage(root, getString(msg));
                }
            });

    @Override
    protected void beforeThemeApplied() {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setKeepOnScreenCondition(() -> loading); // hold until habits are on screen
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        applySystemInsets(root);
        repo = HabitRepository.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setUpToolbar(toolbar, false);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_themes) {
                startActivity(new Intent(this, ThemeGalleryActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        RecyclerView list = findViewById(R.id.habit_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HabitAdapter(this);
        list.setAdapter(adapter);

        ExtendedFloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> onCreateHabit());
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy > 8 && fab.isExtended()) fab.shrink();
                else if (dy < -8 && !fab.isExtended()) fab.extend();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing() || isChangingConfigurations()) return;
        reload();
        if (new ThemePreferences(this).consumeRevokedNotice()) {
            showMessage(root, getString(R.string.event_revoked));
        }
    }

    private void reload() {
        repo.loadHabits(habits -> {
            loading = false;
            if (habits == null) {
                showMessage(root, getString(R.string.error_load_habits));
                return;
            }
            adapter.submit(habits, DayClock.today());
        });
    }

    @Override
    public void onOpenHabit(Habit habit) {
        startActivity(new Intent(this, HabitDetailActivity.class)
                .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, habit.id));
    }

    @Override
    public void onCreateHabit() {
        editLauncher.launch(new Intent(this, EditHabitActivity.class));
    }

    @Override
    public void onToggleHabit(Habit habit) {
        long today = DayClock.today();
        boolean nowDone = !habit.isCompletedOn(today);
        habit.markLocally(today, nowDone); // optimistic: the check responds instantly
        adapter.refreshHabit(habit);
        if (nowDone) {
            root.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
        }
        repo.setCompleted(habit.id, today, nowDone, ok -> {
            if (ok == null || !ok) {
                habit.markLocally(today, !nowDone);
                adapter.refreshHabit(habit);
                showMessage(root, getString(R.string.error_save_completion));
            }
        });
    }
}
