package com.sharebite.app.domain.service;

import com.sharebite.app.data.model.FoodCategory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Aggregated impact figures. Every number is derived from COLLECTED reservations (hand-overs
 * that were confirmed), never from listings merely being posted. No environmental-impact
 * estimates are made because the app has no defensible model for them.
 */
public final class ImpactReport {

    /** One bar of the monthly activity chart. */
    public static final class MonthBucket {
        public final String label;
        public final int quantity;
        public final int collections;

        public MonthBucket(String label, int quantity, int collections) {
            this.label = label;
            this.quantity = quantity;
            this.collections = collections;
        }
    }

    public final int totalQuantityShared;
    public final int completedCollections;
    public final int charityCollections;
    public final int activeListings;
    public final int pendingReservations;
    public final int confirmedReservations;
    /** Distinct counterparts (receivers for a provider, providers for a receiver/charity). */
    public final int distinctPartners;
    /** Quantity by category, in descending order of quantity. */
    public final Map<FoodCategory, Integer> quantityByCategory;
    /** Oldest to newest. */
    public final List<MonthBucket> monthlyActivity;

    public ImpactReport(int totalQuantityShared, int completedCollections, int charityCollections,
                        int activeListings, int pendingReservations, int confirmedReservations,
                        int distinctPartners, Map<FoodCategory, Integer> quantityByCategory,
                        List<MonthBucket> monthlyActivity) {
        this.totalQuantityShared = totalQuantityShared;
        this.completedCollections = completedCollections;
        this.charityCollections = charityCollections;
        this.activeListings = activeListings;
        this.pendingReservations = pendingReservations;
        this.confirmedReservations = confirmedReservations;
        this.distinctPartners = distinctPartners;
        this.quantityByCategory = Collections.unmodifiableMap(quantityByCategory);
        this.monthlyActivity = Collections.unmodifiableList(monthlyActivity);
    }

    public boolean isEmpty() {
        return completedCollections == 0;
    }
}
