package com.sharebite.app.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.service.ListingItem;
import com.sharebite.app.domain.service.ListingQuery;
import com.sharebite.app.domain.service.ListingQueryEngine;
import com.sharebite.app.utils.Formatters;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Sections of the receiver/charity home screen, computed in the background. */
public final class HomeFeed {

    static final int SECTION_SIZE = 10;
    /** "Nearby" only includes listings within this radius. */
    static final double NEARBY_RADIUS_KM = 10;

    public final List<ListingItem> nearby;
    public final List<ListingItem> recent;
    public final List<ListingItem> free;
    public final List<ListingItem> discounted;
    public final List<ListingItem> charity;
    public final int totalAvailable;
    public final int endingSoon;
    public final boolean hasLocation;

    private HomeFeed(List<ListingItem> nearby, List<ListingItem> recent, List<ListingItem> free,
                     List<ListingItem> discounted, List<ListingItem> charity, int totalAvailable,
                     int endingSoon, boolean hasLocation) {
        this.nearby = nearby;
        this.recent = recent;
        this.free = free;
        this.discounted = discounted;
        this.charity = charity;
        this.totalAvailable = totalAvailable;
        this.endingSoon = endingSoon;
        this.hasLocation = hasLocation;
    }

    @WorkerThread
    @NonNull
    static HomeFeed build(@NonNull List<FoodListing> listings, @Nullable User user,
                          @Nullable double[] location, @NonNull Date now) {
        List<ListingItem> all = ListingQueryEngine.apply(listings, ListingQuery.defaults(), user, location, now);

        List<ListingItem> nearby = new ArrayList<>();
        if (location != null) {
            for (ListingItem i : ListingQueryEngine.apply(listings, ListingQuery.defaults().withSort(ListingQuery.Sort.NEAREST), user, location, now)) {
                if (i.distanceKm != null && i.distanceKm <= NEARBY_RADIUS_KM) nearby.add(i);
                if (nearby.size() == SECTION_SIZE) break;
            }
        }
        int endingSoon = 0;
        for (ListingItem i : all) if (Formatters.endingWithin(i.listing.getPickupDeadline(), now, 60)) endingSoon++;

        return new HomeFeed(nearby, take(all, null), take(all, DistributionType.FREE),
                take(all, DistributionType.DISCOUNTED), take(all, DistributionType.CHARITY),
                all.size(), endingSoon, location != null);
    }

    private static List<ListingItem> take(List<ListingItem> source, @Nullable DistributionType type) {
        List<ListingItem> out = new ArrayList<>();
        for (ListingItem i : source) {
            if (type == null || i.listing.getDistributionType() == type) out.add(i);
            if (out.size() == SECTION_SIZE) break;
        }
        return out;
    }
}
