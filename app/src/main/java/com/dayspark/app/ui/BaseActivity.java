package com.dayspark.app.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dayspark.app.R;
import com.dayspark.app.billing.PremiumManager;
import com.dayspark.app.theme.AppTheme;
import com.dayspark.app.theme.ThemeController;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

/** Applies the current theme, edge-to-edge insets and shared toolbar behaviour to every screen. */
public abstract class BaseActivity extends AppCompatActivity {

    private static final long REFRESH_INTERVAL_MS = 60_000;
    private static long lastPremiumRefresh;

    private AppTheme appliedTheme;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        beforeThemeApplied();
        appliedTheme = resolveTheme();
        setTheme(appliedTheme.styleRes);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resolveTheme() != appliedTheme) {
            recreate(); // theme changed elsewhere (gallery, purchase, refund)
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastPremiumRefresh > REFRESH_INTERVAL_MS) {
            lastPremiumRefresh = now;
            // Picks up purchases completed outside the app or while it was in the background.
            PremiumManager.get(this).refresh();
        }
    }

    /** Hook for the launch screen, which must be installed before the theme is set. */
    protected void beforeThemeApplied() {}

    protected AppTheme resolveTheme() {
        return ThemeController.effectiveTheme(this);
    }

    protected AppTheme appliedTheme() {
        return appliedTheme;
    }

    protected void setUpToolbar(MaterialToolbar toolbar, boolean showUp) {
        if (showUp) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationContentDescription(R.string.action_back);
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
    }

    /** Pads the root view for status/navigation bars, cutouts and the keyboard. */
    protected void applySystemInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Snackbars are read aloud by TalkBack, so this doubles as the accessibility announcement. */
    protected void showMessage(View anchorRoot, CharSequence message) {
        Snackbar.make(anchorRoot, message, Snackbar.LENGTH_LONG).show();
    }
}
