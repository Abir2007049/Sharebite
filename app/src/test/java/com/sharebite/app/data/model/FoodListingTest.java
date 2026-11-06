package com.sharebite.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sharebite.app.TestData;

import org.junit.Test;

import java.util.Date;

public class FoodListingTest {

    private final Date now = new Date();

    @Test
    public void reserveDecrementsStockAndFlipsToFullyReservedAtZero() throws Exception {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 5, now);
        l.reserve(3, now);
        assertEquals(2, l.getAvailableQuantity());
        assertEquals(ListingStatus.AVAILABLE, l.getStatus());
        l.reserve(2, now);
        assertEquals(0, l.getAvailableQuantity());
        assertEquals(ListingStatus.FULLY_RESERVED, l.getStatus());
    }

    @Test
    public void reserveRejectsMoreThanAvailable() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 2, now);
        assertRule(BusinessRuleException.Reason.INSUFFICIENT_QUANTITY, () -> l.reserve(3, now));
        assertEquals("stock unchanged after a rejected reservation", 2, l.getAvailableQuantity());
    }

    @Test
    public void reserveRejectsNonPositiveQuantity() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 5, now);
        assertRule(BusinessRuleException.Reason.INVALID_QUANTITY, () -> l.reserve(0, now));
        assertRule(BusinessRuleException.Reason.INVALID_QUANTITY, () -> l.reserve(-1, now));
    }

    @Test
    public void reserveRejectsAfterDeadline() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 5, now);
        Date afterDeadline = new Date(now.getTime() + 4 * TestData.HOUR);
        assertRule(BusinessRuleException.Reason.LISTING_EXPIRED, () -> l.reserve(1, afterDeadline));
    }

    @Test
    public void reserveRejectsClosedListings() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 5, now);
        l.setStatus(ListingStatus.CANCELLED);
        assertRule(BusinessRuleException.Reason.LISTING_NOT_AVAILABLE, () -> l.reserve(1, now));
    }

    @Test
    public void releaseReturnsStockAndReopensFullyReservedListing() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 0, now);
        l.release(2);
        assertEquals(2, l.getAvailableQuantity());
        assertEquals(ListingStatus.AVAILABLE, l.getStatus());
    }

    @Test
    public void releaseNeverExceedsTotal() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 4, now);
        l.release(3);
        assertEquals(5, l.getAvailableQuantity());
    }

    @Test
    public void releaseDoesNotReopenWithdrawnListing() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 3, now);
        l.setStatus(ListingStatus.CANCELLED);
        l.release(2);
        assertEquals(ListingStatus.CANCELLED, l.getStatus());
    }

    @Test
    public void changeTotalQuantityPreservesReservedUnits() throws Exception {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 10, 6, now); // 4 reserved
        l.changeTotalQuantity(8);
        assertEquals(8, l.getTotalQuantity());
        assertEquals(4, l.getAvailableQuantity());
        assertRule(BusinessRuleException.Reason.INVALID_QUANTITY, () -> l.changeTotalQuantity(3));
    }

    @Test
    public void changeTotalQuantityToReservedAmountMarksFullyReserved() throws Exception {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 10, 6, now);
        l.changeTotalQuantity(4);
        assertEquals(0, l.getAvailableQuantity());
        assertEquals(ListingStatus.FULLY_RESERVED, l.getStatus());
    }

    @Test
    public void effectiveStatusTreatsOpenListingsPastDeadlineAsExpired() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 5, now);
        assertEquals(ListingStatus.AVAILABLE, l.effectiveStatus(now));
        Date later = new Date(now.getTime() + 5 * TestData.HOUR);
        assertEquals(ListingStatus.EXPIRED, l.effectiveStatus(later));
        assertFalse(l.isReservableAt(later));
        l.setStatus(ListingStatus.COMPLETED);
        assertEquals("terminal states are kept", ListingStatus.COMPLETED, l.effectiveStatus(later));
    }

    @Test
    public void pricingHelpers() {
        FoodListing d = TestData.listing("d", DistributionType.DISCOUNTED, 5, 5, now);
        assertEquals(40.0, d.unitPrice(), 0.0001);
        assertEquals(60, d.discountPercent());
        FoodListing f = TestData.listing("f", DistributionType.FREE, 5, 5, now);
        assertEquals(0.0, f.unitPrice(), 0.0001);
        assertEquals(0, f.discountPercent());
    }

    @Test
    public void allergenDeclarationDetection() {
        FoodListing l = TestData.listing("a", DistributionType.FREE, 5, 5, now);
        l.setAllergens("Gluten, milk");
        assertTrue(l.hasDeclaredAllergens());
        l.setAllergens("None");
        assertFalse(l.hasDeclaredAllergens());
        l.setAllergens("  none declared ");
        assertFalse(l.hasDeclaredAllergens());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertRule(BusinessRuleException.Reason expected, ThrowingRunnable r) {
        try {
            r.run();
            fail("Expected BusinessRuleException " + expected);
        } catch (BusinessRuleException e) {
            assertEquals(expected, e.getReason());
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        }
    }
}
