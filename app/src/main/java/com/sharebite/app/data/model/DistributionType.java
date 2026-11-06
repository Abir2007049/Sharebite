package com.sharebite.app.data.model;

import androidx.annotation.StringRes;

import com.sharebite.app.R;

/** How a provider distributes a listing. */
public enum DistributionType {
    FREE(R.string.distribution_free, R.string.distribution_free_desc),
    DISCOUNTED(R.string.distribution_discounted, R.string.distribution_discounted_desc),
    CHARITY(R.string.distribution_charity, R.string.distribution_charity_desc);

    @StringRes public final int labelRes;
    @StringRes public final int descriptionRes;

    DistributionType(@StringRes int labelRes, @StringRes int descriptionRes) {
        this.labelRes = labelRes;
        this.descriptionRes = descriptionRes;
    }

    public boolean requiresPrice() {
        return this == DISCOUNTED;
    }
}
