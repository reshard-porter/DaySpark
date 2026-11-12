package com.dayspark.app.ui;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.dayspark.app.R;
import com.dayspark.app.billing.PremiumEvent;
import com.dayspark.app.billing.PremiumSnapshot;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

/** Shared wording and button state for the Pro theme purchase (gallery and preview screens). */
final class PurchaseUi {

    private PurchaseUi() {}

    interface Actions {
        void onBuy();

        void onRetry();
    }

    static void bind(Context c, PremiumSnapshot s, TextView status, MaterialButton button,
                     @Nullable CircularProgressIndicator progress, Actions actions) {
        String price = s.formattedPrice;
        boolean busy = false;
        String text;
        String buttonText = null;
        View.OnClickListener click = null;
        switch (s.state) {
            case OWNED:
                text = c.getString(R.string.purchase_status_owned);
                break;
            case READY:
                text = c.getString(R.string.purchase_status_ready);
                buttonText = c.getString(R.string.purchase_button_unlock, price);
                click = v -> actions.onBuy();
                break;
            case PURCHASING:
                text = c.getString(R.string.purchase_status_purchasing);
                busy = true;
                break;
            case PENDING:
                text = c.getString(R.string.purchase_status_pending);
                buttonText = c.getString(R.string.purchase_button_check_status);
                click = v -> actions.onRetry();
                break;
            case VERIFYING:
                text = c.getString(R.string.purchase_status_verifying);
                busy = true;
                break;
            case VERIFICATION_DELAYED:
                text = c.getString(R.string.purchase_status_delayed);
                buttonText = c.getString(R.string.purchase_button_retry);
                click = v -> actions.onRetry();
                break;
            case BILLING_UNAVAILABLE:
                text = c.getString(R.string.purchase_status_billing_unavailable);
                buttonText = c.getString(R.string.purchase_button_retry);
                click = v -> actions.onRetry();
                break;
            case PRODUCT_UNAVAILABLE:
                text = c.getString(R.string.purchase_status_product_unavailable);
                buttonText = c.getString(R.string.purchase_button_retry);
                click = v -> actions.onRetry();
                break;
            case LOADING:
            default:
                text = c.getString(R.string.purchase_status_loading);
                busy = true;
                break;
        }
        if (s.premiumActive) {
            text = c.getString(R.string.purchase_status_owned);
            buttonText = null;
            busy = false;
        }
        if (s.simulated) text = text + "\n" + c.getString(R.string.purchase_simulated_note);
        status.setText(text);
        button.setVisibility(buttonText == null ? View.GONE : View.VISIBLE);
        if (buttonText != null) {
            button.setText(buttonText);
            button.setOnClickListener(click);
        }
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    @StringRes
    static int messageFor(PremiumEvent event) {
        switch (event) {
            case PURCHASE_COMPLETE: return R.string.event_purchase_complete;
            case RESTORED: return R.string.event_restored;
            case PENDING_STARTED: return R.string.event_pending;
            case CANCELLED: return R.string.event_cancelled;
            case DECLINED: return R.string.event_declined;
            case ALREADY_OWNED: return R.string.event_already_owned;
            case NOTHING_TO_RESTORE: return R.string.event_nothing_to_restore;
            case RESTORE_FAILED: return R.string.event_restore_failed;
            case VERIFICATION_FAILED: return R.string.event_verification_failed;
            case REVOKED: return R.string.event_revoked;
            case ERROR:
            default: return R.string.event_error;
        }
    }
}
