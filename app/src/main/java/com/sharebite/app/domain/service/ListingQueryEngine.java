package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Filters, ranks and annotates listings for the discovery screens. Runs on a background thread
 * (it computes a haversine distance per listing and sorts), and is pure Java for unit tests.
 *
 * <p>Expired or sold-out listings are always removed, so they can never be shown as reservable.
 */
public final class ListingQueryEngine {

    private ListingQueryEngine() {
    }

    @WorkerThread
    @NonNull
    public static List<ListingItem> apply(@NonNull List<FoodListing> listings, @NonNull ListingQuery query,
                                          @Nullable User viewer, @Nullable double[] userLatLng,
                                          @NonNull Date now) {
        String needle = query.searchText.toLowerCase(Locale.ROOT);
        List<ListingItem> out = new ArrayList<>();
        for (FoodListing l : listings) {
            if (!l.isReservableAt(now)) continue;
            if (query.category != null && l.getCategory() != query.category) continue;
            if (query.distributionType != null && l.getDistributionType() != query.distributionType) continue;
            if (!needle.isEmpty() && !matches(l, needle)) continue;

            Double distance = null;
            if (userLatLng != null && l.hasCoordinates()) {
                distance = DistanceCalculator.distanceKm(userLatLng[0], userLatLng[1], l.getLatitude(), l.getLongitude());
            }
            boolean reservable = viewer != null && viewer.maxReservableQuantity(l) > 0;
            out.add(new ListingItem(l, distance, reservable));
        }
        Collections.sort(out, comparatorFor(query.sort));
        return out;
    }

    private static boolean matches(FoodListing l, String needle) {
        return contains(l.getTitle(), needle) || contains(l.getDescription(), needle) || contains(l.getProviderName(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    static Comparator<ListingItem> comparatorFor(ListingQuery.Sort sort) {
        Comparator<ListingItem> newest = (a, b) -> Long.compare(millis(b.listing.getCreatedAt()), millis(a.listing.getCreatedAt()));
        switch (sort) {
            case ENDING_SOON:
                return (a, b) -> Long.compare(millis(a.listing.getPickupDeadline()), millis(b.listing.getPickupDeadline()));
            case NEAREST:
                // Listings without a known distance go last, newest first among themselves.
                return (a, b) -> {
                    if (a.distanceKm == null && b.distanceKm == null) return newest.compare(a, b);
                    if (a.distanceKm == null) return 1;
                    if (b.distanceKm == null) return -1;
                    return Double.compare(a.distanceKm, b.distanceKm);
                };
            case NEWEST:
            default:
                return newest;
        }
    }

    private static long millis(com.google.firebase.Timestamp t) {
        // Pending server timestamps (just-created local writes) are treated as "now".
        return t == null ? Long.MAX_VALUE : t.toDate().getTime();
    }
}
