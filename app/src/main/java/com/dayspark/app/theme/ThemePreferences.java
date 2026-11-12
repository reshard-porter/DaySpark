package com.dayspark.app.theme;

import android.content.Context;
import android.content.SharedPreferences;

/** The theme the user picked. Stored apart from ownership so each can change independently. */
public final class ThemePreferences {

    private static final String FILE = "dayspark_theme";
    private static final String K_SELECTED = "selected";
    private static final String K_REVOKED_NOTICE = "revoked_notice";

    private final SharedPreferences prefs;

    public ThemePreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public AppTheme selected() {
        return AppTheme.fromId(prefs.getString(K_SELECTED, AppTheme.DAYSPARK.id));
    }

    public void select(AppTheme theme) {
        prefs.edit().putString(K_SELECTED, theme.id).apply();
    }

    /** After a refund: go back to the default theme and remember to tell the user once. */
    public void resetAfterRevocation() {
        if (selected().premium) {
            prefs.edit().putString(K_SELECTED, AppTheme.DAYSPARK.id).putBoolean(K_REVOKED_NOTICE, true).apply();
        }
    }

    /** Returns true once after {@link #resetAfterRevocation()} changed the theme. */
    public boolean consumeRevokedNotice() {
        boolean show = prefs.getBoolean(K_REVOKED_NOTICE, false);
        if (show) prefs.edit().putBoolean(K_REVOKED_NOTICE, false).apply();
        return show;
    }
}
