package com.sharebite.app.domain.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sharebite.app.TestData;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.data.model.Receiver;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ListingQueryEngineTest {

    private final Date now = new Date();
    private final Receiver viewer = new Receiver("r", "Rita", "r@example.com", "123456789");

    @Test
    public void expiredSoldOutAndClosedListingsAreNeverShown() {
        FoodListing ok = TestData.listing("ok", DistributionType.FREE, 5, 5, now);
        FoodListing soldOut = TestData.listing("sold", DistributionType.FREE, 5, 0, now);
        FoodListing expired = TestData.listing("exp", DistributionType.FREE, 5, 5, now);
        expired.setPickupDeadline(TestData.at(now, -1));
        FoodListing cancelled = TestData.listing("can", DistributionType.FREE, 5, 5, now);
        cancelled.setStatus(ListingStatus.CANCELLED);

        List<ListingItem> out = ListingQueryEngine.apply(Arrays.asList(ok, soldOut, expired, cancelled),
                ListingQuery.defaults(), viewer, null, now);
        assertEquals(1, out.size());
        assertEquals("ok", out.get(0).listing.getListingId());
    }

    @Test
    public void filtersBySearchCategoryAndType() {
        FoodListing bread = TestData.listing("bread", DistributionType.FREE, 5, 5, now);
        bread.setTitle("Sourdough bread");
        FoodListing curry = TestData.listing("curry", DistributionType.DISCOUNTED, 5, 5, now);
        curry.setTitle("Chicken curry");
        curry.setCategory(FoodCategory.COOKED_MEALS);
        List<FoodListing> all = Arrays.asList(bread, curry);

        assertEquals("curry", ListingQueryEngine.apply(all, ListingQuery.defaults().withSearch("CURRY"), viewer, null, now)
                .get(0).listing.getListingId());
        assertEquals(1, ListingQueryEngine.apply(all, ListingQuery.defaults().withCategory(FoodCategory.BAKERY), viewer, null, now).size());
        assertEquals(1, ListingQueryEngine.apply(all, ListingQuery.defaults().withDistribution(DistributionType.DISCOUNTED), viewer, null, now).size());
        assertEquals("provider name is searchable", 2,
                ListingQueryEngine.apply(all, ListingQuery.defaults().withSearch("green bakery"), viewer, null, now).size());
    }

    @Test
    public void distanceOnlyWhenBothLocationsAreKnown_andNearestSortPutsUnknownLast() {
        FoodListing near = TestData.listing("near", DistributionType.FREE, 5, 5, now);
        near.setLatitude(23.80);
        near.setLongitude(90.41);
        FoodListing far = TestData.listing("far", DistributionType.FREE, 5, 5, now);
        far.setLatitude(24.50);
        far.setLongitude(90.41);
        FoodListing unknown = TestData.listing("unknown", DistributionType.FREE, 5, 5, now);
        List<FoodListing> all = Arrays.asList(unknown, far, near);

        List<ListingItem> noLocation = ListingQueryEngine.apply(all, ListingQuery.defaults(), viewer, null, now);
        for (ListingItem i : noLocation) assertNull("no fabricated distances", i.distanceKm);

        double[] me = {23.78, 90.41};
        List<ListingItem> sorted = ListingQueryEngine.apply(all, ListingQuery.defaults().withSort(ListingQuery.Sort.NEAREST), viewer, me, now);
        assertEquals("near", sorted.get(0).listing.getListingId());
        assertEquals("far", sorted.get(1).listing.getListingId());
        assertEquals("unknown", sorted.get(2).listing.getListingId());
        assertNotNull(sorted.get(0).distanceKm);
        assertTrue(sorted.get(0).distanceKm < 3);
    }

    @Test
    public void reservabilityReflectsTheViewersRole() {
        FoodListing charity = TestData.listing("c", DistributionType.CHARITY, 5, 5, now);
        List<ListingItem> out = ListingQueryEngine.apply(Arrays.asList(charity), ListingQuery.defaults(), viewer, null, now);
        assertEquals(1, out.size());
        assertFalse("individuals see charity listings but cannot reserve them", out.get(0).reservableByUser);
    }

    @Test
    public void endingSoonSortsByDeadline() {
        FoodListing later = TestData.listing("later", DistributionType.FREE, 5, 5, now);
        FoodListing sooner = TestData.listing("sooner", DistributionType.FREE, 5, 5, now);
        sooner.setPickupDeadline(TestData.at(now, TestData.HOUR));
        List<ListingItem> out = ListingQueryEngine.apply(Arrays.asList(later, sooner),
                ListingQuery.defaults().withSort(ListingQuery.Sort.ENDING_SOON), viewer, null, now);
        assertEquals("sooner", out.get(0).listing.getListingId());
    }
}
