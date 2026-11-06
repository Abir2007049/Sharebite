package com.sharebite.app.data.model;

import androidx.annotation.StringRes;

import com.sharebite.app.R;

/** Kind of food provider; shown on listings so receivers know who they are collecting from. */
public enum ProviderType {
    RESTAURANT(R.string.provider_type_restaurant),
    BAKERY(R.string.provider_type_bakery),
    CAMPUS_CANTEEN(R.string.provider_type_canteen),
    HOUSEHOLD(R.string.provider_type_household),
    OTHER(R.string.provider_type_other);

    @StringRes public final int labelRes;

    ProviderType(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    /** Households are individuals; every other provider type is a business/organisation. */
    public boolean isBusiness() {
        return this != HOUSEHOLD;
    }
}
