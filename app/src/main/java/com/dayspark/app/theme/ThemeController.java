package com.dayspark.app.theme;

import android.app.Activity;
import android.content.Context;

import com.dayspark.app.billing.PremiumManager;

/**
 * Decides which theme is actually applied. Knows nothing about billing internals: it only asks
 * {@link PremiumManager#isPremiumActive()} and never unlocks anything itself.
 */
public final class ThemeController {

    private ThemeController() {}

    /** The selected theme if usable, otherwise the free default. */
    public static AppTheme effectiveTheme(Context context) {
        AppTheme selected = new ThemePreferences(context).selected();
        if (selected.premium && !PremiumManager.get(context).isPremiumActive()) {
            return AppTheme.DAYSPARK;
        }
        return selected;
    }

    /** Call before {@code super.onCreate()} in every activity. */
    public static AppTheme applyTo(Activity activity) {
        AppTheme theme = effectiveTheme(activity);
        activity.setTheme(theme.styleRes);
        return theme;
    }

    public static boolean canApply(Context context, AppTheme theme) {
        return !theme.premium || PremiumManager.get(context).isPremiumActive();
    }

    /** Returns false (and changes nothing) when a premium theme is not owned. */
    public static boolean select(Context context, AppTheme theme) {
        if (!canApply(context, theme)) return false;
        new ThemePreferences(context).select(theme);
        return true;
    }
}
