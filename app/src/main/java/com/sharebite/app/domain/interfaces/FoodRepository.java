package com.sharebite.app.domain.interfaces;

import androidx.annotation.Nullable;

import com.sharebite.app.data.model.FoodListing;

import java.util.List;

/** CRUD and queries for foodListings/{listingId}. */
public interface FoodRepository extends Repository<FoodListing, String> {

    /** Reserves a new document ID (used to name the image file before the listing exists). */
    String newListingId();

    /** Creates the listing; {@code imageBytes} (already compressed) is uploaded first if given. */
    void createListing(FoodListing listing, @Nullable byte[] imageBytes, ResultCallback<FoodListing> callback);

    /**
     * Updates provider-editable fields. Quantity changes run in a transaction so units already
     * reserved by others are never lost.
     */
    void updateListing(FoodListing edited, @Nullable byte[] newImageBytes, ResultCallback<FoodListing> callback);

    /** Withdraws the listing and rejects its open reservations (notifying those users). */
    void cancelListing(FoodListing listing, ResultCallback<Void> callback);

    /** Permanently deletes a listing that has never been reserved. */
    void deleteListing(FoodListing listing, ResultCallback<Void> callback);

    /** Provider closes a listing once all hand-overs are done. */
    void markCompleted(FoodListing listing, ResultCallback<Void> callback);

    /** Marks the provider's open listings whose deadline has passed as EXPIRED. Returns the count. */
    void expireOverdueListings(String providerId, ResultCallback<Integer> callback);

    /** Realtime stream of listings currently AVAILABLE (expired ones are filtered by callers). */
    Subscription observeAvailableListings(ResultCallback<List<FoodListing>> callback);

    Subscription observeProviderListings(String providerId, ResultCallback<List<FoodListing>> callback);
}
