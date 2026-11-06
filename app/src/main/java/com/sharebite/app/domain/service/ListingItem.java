package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sharebite.app.data.model.FoodListing;

/** A listing prepared for display: with its distance from the user, when that is known. */
public final class ListingItem {

    @NonNull public final FoodListing listing;
    /** Null when the user's location or the listing's coordinates are unavailable. */
    @Nullable public final Double distanceKm;
    /** Whether the current user may reserve it (role + availability). */
    public final boolean reservableByUser;

    public ListingItem(@NonNull FoodListing listing, @Nullable Double distanceKm, boolean reservableByUser) {
        this.listing = listing;
        this.distanceKm = distanceKm;
        this.reservableByUser = reservableByUser;
    }
}
