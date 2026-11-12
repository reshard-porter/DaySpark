package com.dayspark.app.theme;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import com.dayspark.app.R;

/** Every theme in the gallery. Colors live in res/values/colors.xml and themes.xml. */
public enum AppTheme {
    DAYSPARK("dayspark", R.string.theme_dayspark, R.string.theme_dayspark_desc, R.style.Theme_DaySpark, false),
    FOREST("forest", R.string.theme_forest, R.string.theme_forest_desc, R.style.Theme_DaySpark_Forest, true),
    OCEAN("ocean", R.string.theme_ocean, R.string.theme_ocean_desc, R.style.Theme_DaySpark_Ocean, true),
    LAVENDER("lavender", R.string.theme_lavender, R.string.theme_lavender_desc, R.style.Theme_DaySpark_Lavender, true),
    SUNSET("sunset", R.string.theme_sunset, R.string.theme_sunset_desc, R.style.Theme_DaySpark_Sunset, true);

    public final String id;
    @StringRes public final int nameRes;
    @StringRes public final int descriptionRes;
    @StyleRes public final int styleRes;
    public final boolean premium;

    AppTheme(String id, int nameRes, int descriptionRes, int styleRes, boolean premium) {
        this.id = id;
        this.nameRes = nameRes;
        this.descriptionRes = descriptionRes;
        this.styleRes = styleRes;
        this.premium = premium;
    }

    public static AppTheme fromId(String id) {
        for (AppTheme t : values()) if (t.id.equals(id)) return t;
        return DAYSPARK;
    }
}
