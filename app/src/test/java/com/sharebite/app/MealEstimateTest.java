package com.sharebite.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sharebite.app.data.model.QuantityUnit;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.domain.service.MealEstimate;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MealEstimateTest {

    private static Reservation r(QuantityUnit unit, int qty, ReservationStatus status) {
        Reservation r = new Reservation();
        r.setUnit(unit);
        r.setQuantity(qty);
        r.setStatus(status);
        return r;
    }

    @Test
    public void portionsCountAsMealsAndKilogramsUseTheWrapFactor() {
        MealEstimate e = MealEstimate.of(Arrays.asList(
                r(QuantityUnit.PORTIONS, 4, ReservationStatus.COLLECTED),
                r(QuantityUnit.KILOGRAMS, 2, ReservationStatus.COLLECTED)));
        // 4 portions + floor(2000 g / 420 g) = 4 + 4
        assertEquals(8, e.meals());
        assertEquals(2, e.kilograms);
    }

    @Test
    public void onlyCollectedReservationsCount() {
        MealEstimate e = MealEstimate.of(Arrays.asList(
                r(QuantityUnit.PORTIONS, 5, ReservationStatus.CONFIRMED),
                r(QuantityUnit.PORTIONS, 5, ReservationStatus.CANCELLED)));
        assertTrue(e.isEmpty());
        assertEquals(0, e.meals());
    }

    @Test
    public void unitsWithoutAKnownWeightAreReportedNotGuessed() {
        MealEstimate e = MealEstimate.of(Arrays.asList(
                r(QuantityUnit.BOXES, 3, ReservationStatus.COLLECTED),
                r(QuantityUnit.LITRES, 10, ReservationStatus.COLLECTED)));
        assertEquals(0, e.meals());
        assertEquals(2, e.unconvertedCollections);
        assertEquals(0, MealEstimate.of(Collections.emptyList()).meals());
    }
}
