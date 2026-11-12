package com.dayspark.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.dayspark.app.BuildConfig;
import com.dayspark.app.R;
import com.dayspark.app.billing.BillingFactory;
import com.dayspark.app.billing.PremiumEngine;
import com.dayspark.app.billing.PremiumEvent;
import com.dayspark.app.billing.PremiumManager;
import com.dayspark.app.billing.PremiumSnapshot;
import com.dayspark.app.theme.ThemeController;
import com.google.android.material.appbar.MaterialToolbar;

public final class SettingsActivity extends BaseActivity implements PremiumEngine.Listener {

    private View root;
    private TextView proValue;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        root = findViewById(R.id.root);
        applySystemInsets(root);
        setUpToolbar(findViewById(R.id.toolbar), true);

        ((TextView) findViewById(R.id.row_theme_value))
                .setText(ThemeController.effectiveTheme(this).nameRes);
        findViewById(R.id.row_theme).setOnClickListener(v ->
                startActivity(new Intent(this, ThemeGalleryActivity.class)));
        findViewById(R.id.row_pro).setOnClickListener(v ->
                startActivity(new Intent(this, ThemeGalleryActivity.class)));
        findViewById(R.id.row_restore).setOnClickListener(v -> PremiumManager.get(this).restore());
        proValue = findViewById(R.id.row_pro_value);
        ((TextView) findViewById(R.id.row_about_value))
                .setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        if (BillingFactory.hasDebugTools()) {
            findViewById(R.id.debug_divider).setVisibility(View.VISIBLE);
            View debug = findViewById(R.id.row_debug);
            debug.setVisibility(View.VISIBLE);
            debug.setOnClickListener(v -> BillingFactory.showDebugTools(this));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        PremiumManager.get(this).addListener(this);
    }

    @Override
    protected void onStop() {
        PremiumManager.get(this).removeListener(this);
        super.onStop();
    }

    @Override
    public void onPremiumChanged(PremiumSnapshot s) {
        int text;
        if (s.premiumActive) text = R.string.pro_value_owned;
        else {
            switch (s.state) {
                case PENDING: text = R.string.pro_value_pending; break;
                case VERIFYING:
                case VERIFICATION_DELAYED: text = R.string.pro_value_checking; break;
                default: text = R.string.pro_value_not_owned;
            }
        }
        proValue.setText(text);
    }

    @Override
    public void onPremiumEvent(PremiumEvent event) {
        showMessage(root, getString(PurchaseUi.messageFor(event)));
        if (event == PremiumEvent.REVOKED) recreate();
    }
}
