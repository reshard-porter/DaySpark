package com.dayspark.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

import com.dayspark.app.R;
import com.dayspark.app.billing.PremiumEngine;
import com.dayspark.app.billing.PremiumEvent;
import com.dayspark.app.billing.PremiumManager;
import com.dayspark.app.billing.PremiumSnapshot;
import com.dayspark.app.billing.PremiumState;
import com.dayspark.app.theme.AppTheme;
import com.dayspark.app.theme.ThemeController;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

/** Free default plus four Pro themes. Anyone can preview; applying a Pro theme needs ownership. */
public final class ThemeGalleryActivity extends BaseActivity implements PremiumEngine.Listener {

    static final String EXTRA_APPLIED = "applied_theme";
    private static final String STATE_PENDING_THEME = "pending_theme";

    private PremiumManager premium;
    private View root;
    private LinearLayout list;
    private TextView status;
    private MaterialButton buyButton;
    private CircularProgressIndicator progress;
    /** Theme to switch to once a purchase started from its "Unlock" button completes. */
    private AppTheme pendingTheme;

    private final PurchaseUi.Actions actions = new PurchaseUi.Actions() {
        @Override public void onBuy() { premium.purchase(ThemeGalleryActivity.this); }

        @Override public void onRetry() { premium.restore(); }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_gallery);
        root = findViewById(R.id.root);
        applySystemInsets(root);
        setUpToolbar(findViewById(R.id.toolbar), true);
        premium = PremiumManager.get(this);

        View card = findViewById(R.id.purchase_card);
        status = card.findViewById(R.id.purchase_status);
        buyButton = card.findViewById(R.id.purchase_button);
        progress = card.findViewById(R.id.purchase_progress);
        card.findViewById(R.id.purchase_restore).setOnClickListener(v -> premium.restore());
        list = findViewById(R.id.theme_list);

        if (savedInstanceState != null && savedInstanceState.getString(STATE_PENDING_THEME) != null) {
            pendingTheme = AppTheme.fromId(savedInstanceState.getString(STATE_PENDING_THEME));
        }
        String applied = getIntent().getStringExtra(EXTRA_APPLIED);
        if (applied != null) {
            getIntent().removeExtra(EXTRA_APPLIED);
            showMessage(root, getString(R.string.msg_theme_applied,
                    getString(AppTheme.fromId(applied).nameRes)));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingTheme != null) outState.putString(STATE_PENDING_THEME, pendingTheme.id);
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
    public void onPremiumChanged(PremiumSnapshot snapshot) {
        PurchaseUi.bind(this, snapshot, status, buyButton, progress, actions);
        buildThemeList(snapshot);
    }

    @Override
    public void onPremiumEvent(PremiumEvent event) {
        if (event == PremiumEvent.PURCHASE_COMPLETE && pendingTheme != null) {
            AppTheme theme = pendingTheme;
            pendingTheme = null;
            applyTheme(theme);
            return;
        }
        if (event == PremiumEvent.CANCELLED || event == PremiumEvent.DECLINED
                || event == PremiumEvent.ERROR) {
            pendingTheme = null;
        }
        showMessage(root, getString(PurchaseUi.messageFor(event)));
        if (event == PremiumEvent.REVOKED) recreate();
    }

    private void buildThemeList(PremiumSnapshot s) {
        list.removeAllViews();
        AppTheme current = appliedTheme();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AppTheme theme : AppTheme.values()) {
            View item = inflater.inflate(R.layout.item_theme, list, false);
            String name = getString(theme.nameRes);

            FrameLayout previewBox = item.findViewById(R.id.theme_preview_container);
            View preview = LayoutInflater.from(new ContextThemeWrapper(this, theme.styleRes))
                    .inflate(R.layout.view_theme_preview, previewBox, false);
            previewBox.addView(preview, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            previewBox.setContentDescription(getString(R.string.cd_theme_preview, name));
            previewBox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

            ((TextView) item.findViewById(R.id.theme_name)).setText(name);
            ((TextView) item.findViewById(R.id.theme_description)).setText(theme.descriptionRes);

            TextView badge = item.findViewById(R.id.theme_badge);
            boolean inUse = theme == current;
            boolean locked = theme.premium && !s.premiumActive;
            if (inUse) badge.setText(R.string.badge_in_use);
            else if (!theme.premium) badge.setText(R.string.badge_free);
            else badge.setText(locked ? R.string.badge_pro_locked : R.string.badge_pro);
            badge.setCompoundDrawablesRelativeWithIntrinsicBounds(locked ? R.drawable.ic_lock : 0, 0, 0, 0);

            MaterialButton previewButton = item.findViewById(R.id.theme_preview_button);
            previewButton.setVisibility(theme.premium && !inUse ? View.VISIBLE : View.GONE);
            previewButton.setContentDescription(getString(R.string.cd_preview_theme, name));
            previewButton.setOnClickListener(v -> startActivity(
                    new Intent(this, ThemePreviewActivity.class)
                            .putExtra(ThemePreviewActivity.EXTRA_THEME, theme.id)));

            MaterialButton apply = item.findViewById(R.id.theme_apply_button);
            if (inUse) {
                apply.setVisibility(View.GONE);
            } else if (locked) {
                apply.setText(R.string.action_unlock);
                apply.setIconResource(R.drawable.ic_lock);
                apply.setContentDescription(getString(R.string.cd_unlock_theme, name));
                apply.setOnClickListener(v -> offerUnlock(theme, s));
            } else {
                apply.setText(R.string.action_apply);
                apply.setContentDescription(getString(R.string.cd_apply_theme, name));
                apply.setOnClickListener(v -> applyTheme(theme));
            }
            list.addView(item);
        }
    }

    private void offerUnlock(AppTheme theme, PremiumSnapshot s) {
        if (s.state != PremiumState.READY || s.formattedPrice == null) {
            ((ScrollView) list.getParent().getParent()).smoothScrollTo(0, 0);
            showMessage(root, status.getText());
            return;
        }
        String name = getString(theme.nameRes);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_unlock_title, name))
                .setMessage(getString(R.string.dialog_unlock_body, name, s.formattedPrice))
                .setPositiveButton(getString(R.string.purchase_button_unlock, s.formattedPrice), (d, w) -> {
                    pendingTheme = theme;
                    premium.purchase(this);
                })
                .setNegativeButton(R.string.action_not_now, null)
                .show();
    }

    private void applyTheme(AppTheme theme) {
        if (!ThemeController.select(this, theme)) return; // not owned: nothing changes
        getIntent().putExtra(EXTRA_APPLIED, theme.id);
        recreate();
    }
}
