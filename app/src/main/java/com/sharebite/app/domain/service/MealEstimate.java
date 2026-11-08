package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;

import com.sharebite.app.data.model.QuantityUnit;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;

import java.util.List;

/**
 * A transparent "meals rescued" estimate for collected food. The model is deliberately simple and
 * shown to the user in full:
 *
 * <ul>
 *   <li>{@code PORTIONS}: one portion counts as one meal (that is what the unit means);</li>
 *   <li>{@code KILOGRAMS}: weight divided by {@link #GRAMS_PER_MEAL}, the conversion factor
 *       WRAP (UK) uses for redistributed surplus food;</li>
 *   <li>pieces, boxes, packs and litres have no known weight, so they are <em>not</em> converted and
 *       are reported separately instead of guessed.</li>
 * </ul>
 *
 * No carbon or monetary figures are derived from this.
 */
public final class MealEstimate {

    public static final double GRAMS_PER_MEAL = 420.0;

    public final int portions;
    public final int kilograms;
    /** Collected reservations in units without a known weight (not converted). */
    public final int unconvertedCollections;

    public MealEstimate(int portions, int kilograms, int unconvertedCollections) {
        this.portions = portions;
        this.kilograms = kilograms;
        this.unconvertedCollections = unconvertedCollections;
    }

    /** Estimated meals, rounded down so the figure is never overstated. */
    public int meals() {
        return portions + (int) Math.floor(kilograms * 1000.0 / GRAMS_PER_MEAL);
    }

    public boolean isEmpty() {
        return portions == 0 && kilograms == 0;
    }

    /** Builds the estimate from reservations; only COLLECTED ones count. */
    @NonNull
    public static MealEstimate of(@NonNull List<Reservation> reservations) {
        int portions = 0, kg = 0, other = 0;
        for (Reservation r : reservations) {
            if (r.getStatus() != ReservationStatus.COLLECTED) continue;
            QuantityUnit unit = r.getUnit();
            if (unit == QuantityUnit.PORTIONS) portions += r.getQuantity();
            else if (unit == QuantityUnit.KILOGRAMS) kg += r.getQuantity();
            else other++;
        }
        return new MealEstimate(portions, kg, other);
    }
}
