package com.dayspark.app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dayspark.app.R;
import com.dayspark.app.billing.PremiumEngine;
import com.dayspark.app.billing.PremiumEvent;
import com.dayspark.app.billing.PremiumManager;
import com.dayspark.app.billing.PremiumSnapshot;
import com.dayspark.app.data.DayClock;
import com.dayspark.app.data.Habit;
import com.dayspark.app.theme.AppTheme;
import com.dayspark.app.theme.ThemeController;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen try-on of any theme using sample habits. Nothing is saved: leaving this screen
 * returns to the user's own theme. Applying a Pro theme still requires ownership.
 */
public final class ThemePreviewActivity extends BaseActivity implements PremiumEngine.Listener {

    public static final String EXTRA_THEME = "theme_id";

    private PremiumManager premium;
    private AppTheme theme;
    private View root;
    private View purchaseCard;
    private MaterialButton applyButton;
    private boolean purchaseStartedHere;

    @Override
    protected AppTheme resolveTheme() {
        return AppTheme.fromId(getIntent().getStringExtra(EXTRA_THEME));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_preview);
        root = findViewById(R.id.root);
        applySystemInsets(root);
        premium = PremiumManager.get(this);
        theme = appliedTheme();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setUpToolbar(toolbar, true);
        toolbar.setTitle(getString(R.string.title_preview, getString(theme.nameRes)));

        RecyclerView list = findViewById(R.id.preview_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        HabitAdapter adapter = new HabitAdapter(new HabitAdapter.Listener() {
            @Override public void onOpenHabit(Habit habit) {}

            @Override public void onToggleHabit(Habit habit) {} // sample data, read-only

            @Override public void onCreateHabit() {}
        });
        list.setAdapter(adapter);
        adapter.submit(sampleHabits(), DayClock.today());

        purchaseCard = findViewById(R.id.purchase_card);
        purchaseCard.findViewById(R.id.purchase_restore).setOnClickListener(v -> premium.restore());
        applyButton = findViewById(R.id.preview_apply);
        applyButton.setText(getString(R.string.action_use_named_theme, getString(theme.nameRes)));
        applyButton.setOnClickListener(v -> {
            if (ThemeController.select(this, theme)) {
                showMessage(root, getString(R.string.msg_theme_applied, getString(theme.nameRes)));
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        premium.addListener(this);
    }

    @Override
    protected void onStop() {
        premium.removeListener(this);
        super.onStop();
    }

    @Override
    public void onPremiumChanged(PremiumSnapshot s) {
        boolean canApply = !theme.premium || s.premiumActive;
        purchaseCard.setVisibility(canApply ? View.GONE : View.VISIBLE);
        applyButton.setVisibility(canApply ? View.VISIBLE : View.GONE);
        if (!canApply) {
            PurchaseUi.bind(this, s, purchaseCard.findViewById(R.id.purchase_status),
                    purchaseCard.findViewById(R.id.purchase_button),
                    (CircularProgressIndicator) purchaseCard.findViewById(R.id.purchase_progress),
                    new PurchaseUi.Actions() {
                        @Override public void onBuy() {
                            purchaseStartedHere = true;
                            premium.purchase(ThemePreviewActivity.this);
                        }

                        @Override public void onRetry() { premium.restore(); }
                    });
        }
    }

    @Override
    public void onPremiumEvent(PremiumEvent event) {
        if (event == PremiumEvent.PURCHASE_COMPLETE && purchaseStartedHere) {
            purchaseStartedHere = false;
            ThemeController.select(this, theme); // bought while previewing: use it right away
            showMessage(root, getString(R.string.msg_theme_applied, getString(theme.nameRes)));
            return;
        }
        showMessage(root, getString(PurchaseUi.messageFor(event)));
    }

    private List<Habit> sampleHabits() {
        long today = DayClock.today();
        List<Habit> list = new ArrayList<>();
        list.add(sample(1, getString(R.string.sample_habit_walk), Habit.EVERY_DAY, today, 6, true));
        list.add(sample(2, getString(R.string.sample_habit_read), Habit.EVERY_DAY, today, 3, false));
        list.add(sample(3, getString(R.string.sample_habit_water), Habit.WEEKDAYS, today, 9, false));
        return list;
    }

    private static Habit sample(long id, String name, int mask, long today, int days, boolean doneToday) {
        Habit h = new Habit();
        h.id = id;
        h.name = name;
        h.daysMask = mask;
        h.createdDay = today - 30;
        for (long d = today - 1, added = 0; added < days && d > today - 30; d--) {
            if (h.isScheduledOn(d)) {
                h.markLocally(d, true);
                added++;
            }
        }
        if (doneToday) h.markLocally(today, true);
        return h;
    }
}
