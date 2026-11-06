package com.sharebite.app.data.model;

import androidx.annotation.StringRes;

import com.sharebite.app.R;

/** Lifecycle of a food listing. */
public enum ListingStatus {
    AVAILABLE(R.string.listing_status_available),
    FULLY_RESERVED(R.string.listing_status_fully_reserved),
    EXPIRED(R.string.listing_status_expired),
    COMPLETED(R.string.listing_status_completed),
    CANCELLED(R.string.listing_status_cancelled);

    @StringRes public final int labelRes;

    ListingStatus(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    /** Listings in these states are still "live" (deadline not yet handled by the provider). */
    public boolean isOpen() {
        return this == AVAILABLE || this == FULLY_RESERVED;
    }
}
