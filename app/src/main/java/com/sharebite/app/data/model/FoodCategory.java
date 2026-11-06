package com.sharebite.app.data.model;

import androidx.annotation.StringRes;

import com.sharebite.app.R;

/** Food categories a listing can belong to. */
public enum FoodCategory {
    COOKED_MEALS(R.string.category_cooked_meals, true),
    BAKERY(R.string.category_bakery, false),
    FRUITS(R.string.category_fruits, false),
    VEGETABLES(R.string.category_vegetables, false),
    DAIRY(R.string.category_dairy, true),
    PACKAGED(R.string.category_packaged, false),
    OTHER(R.string.category_other, false);

    @StringRes public final int labelRes;
    /**
     * Foods that spoil quickly at room temperature. Used to show extra handling guidance
     * (e.g. when the pickup location is hot). This is guidance, not a safety certification.
     */
    public final boolean temperatureSensitive;

    FoodCategory(@StringRes int labelRes, boolean temperatureSensitive) {
        this.labelRes = labelRes;
        this.temperatureSensitive = temperatureSensitive;
    }
}
