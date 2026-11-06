package com.sharebite.app.domain.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sharebite.app.TestData;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.UserRole;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ImpactCalculatorTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private final Date now = date(2026, Calendar.NOVEMBER, 15);

    @Test
    public void onlyCollectedReservationsCount() {
        List<Reservation> rs = Arrays.asList(
                TestData.reservation("1", ReservationStatus.COLLECTED, 4, FoodCategory.BAKERY, "a", UserRole.RECEIVER, DistributionType.FREE, ts(now)),
                TestData.reservation("2", ReservationStatus.PENDING, 10, FoodCategory.BAKERY, "b", UserRole.RECEIVER, DistributionType.FREE, null),
                TestData.reservation("3", ReservationStatus.CONFIRMED, 7, FoodCategory.DAIRY, "b", UserRole.RECEIVER, DistributionType.FREE, null),
                TestData.reservation("4", ReservationStatus.CANCELLED, 9, FoodCategory.DAIRY, "c", UserRole.RECEIVER, DistributionType.FREE, null),
                TestData.reservation("5", ReservationStatus.REJECTED, 9, FoodCategory.DAIRY, "c", UserRole.RECEIVER, DistributionType.FREE, null));
        ImpactReport r = ImpactCalculator.calculate(UserRole.PROVIDER, rs, Collections.emptyList(), now, UTC, Locale.US);
        assertEquals(4, r.totalQuantityShared);
        assertEquals(1, r.completedCollections);
        assertEquals(1, r.pendingReservations);
        assertEquals(1, r.confirmedReservations);
        assertEquals(1, r.distinctPartners);
    }

    @Test
    public void charityCollectionsAndCategoriesAreAggregated() {
        List<Reservation> rs = Arrays.asList(
                TestData.reservation("1", ReservationStatus.COLLECTED, 20, FoodCategory.COOKED_MEALS, "ch", UserRole.CHARITY, DistributionType.CHARITY, ts(now)),
                TestData.reservation("2", ReservationStatus.COLLECTED, 3, FoodCategory.BAKERY, "a", UserRole.RECEIVER, DistributionType.FREE, ts(now)),
                TestData.reservation("3", ReservationStatus.COLLECTED, 2, FoodCategory.BAKERY, "a", UserRole.RECEIVER, DistributionType.DISCOUNTED, ts(now)));
        ImpactReport r = ImpactCalculator.calculate(UserRole.PROVIDER, rs, Collections.emptyList(), now, UTC, Locale.US);
        assertEquals(25, r.totalQuantityShared);
        assertEquals(1, r.charityCollections);
        assertEquals(2, r.distinctPartners);
        List<FoodCategory> order = new ArrayList<>(r.quantityByCategory.keySet());
        assertEquals("largest category first", FoodCategory.COOKED_MEALS, order.get(0));
        assertEquals(Integer.valueOf(5), r.quantityByCategory.get(FoodCategory.BAKERY));
    }

    @Test
    public void monthlyBucketsCoverTheLastSixMonths() {
        List<Reservation> rs = Arrays.asList(
                TestData.reservation("now", ReservationStatus.COLLECTED, 3, FoodCategory.FRUITS, "a", UserRole.RECEIVER, DistributionType.FREE, ts(now)),
                TestData.reservation("jul", ReservationStatus.COLLECTED, 5, FoodCategory.FRUITS, "a", UserRole.RECEIVER, DistributionType.FREE, ts(date(2026, Calendar.JULY, 3))),
                TestData.reservation("old", ReservationStatus.COLLECTED, 99, FoodCategory.FRUITS, "a", UserRole.RECEIVER, DistributionType.FREE, ts(date(2025, Calendar.DECEMBER, 1))));
        ImpactReport r = ImpactCalculator.calculate(UserRole.RECEIVER, rs, Collections.emptyList(), now, UTC, Locale.US);
        assertEquals(6, r.monthlyActivity.size());
        assertEquals("Jun", r.monthlyActivity.get(0).label);
        assertEquals("Nov", r.monthlyActivity.get(5).label);
        assertEquals(3, r.monthlyActivity.get(5).quantity);
        assertEquals(5, r.monthlyActivity.get(1).quantity);
        int sum = 0;
        for (ImpactReport.MonthBucket b : r.monthlyActivity) sum += b.quantity;
        assertEquals("collections older than six months are excluded from the chart", 8, sum);
        assertEquals("but still count towards the total", 107, r.totalQuantityShared);
    }

    @Test
    public void activeListingsUseEffectiveStatus() {
        FoodListing open = TestData.listing("o", DistributionType.FREE, 5, 5, new Date());
        FoodListing expired = TestData.listing("e", DistributionType.FREE, 5, 5, new Date());
        expired.setPickupDeadline(TestData.at(new Date(), -TestData.HOUR));
        ImpactReport r = ImpactCalculator.calculate(UserRole.PROVIDER, Collections.emptyList(),
                Arrays.asList(open, expired), new Date(), UTC, Locale.US);
        assertEquals(1, r.activeListings);
        assertTrue(r.isEmpty());
    }

    private static Date date(int y, int m, int d) {
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(y, m, d, 12, 0);
        return c.getTime();
    }

    private static com.google.firebase.Timestamp ts(Date d) {
        return new com.google.firebase.Timestamp(d);
    }
}
