package com.sharebite.app;

import com.google.firebase.Timestamp;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.data.model.QuantityUnit;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.UserRole;

import java.util.Date;

/** Builders for test fixtures. */
public final class TestData {

    public static final long HOUR = 3_600_000L;

    private TestData() {
    }

    public static Timestamp at(Date base, long offsetMs) {
        return new Timestamp(new Date(base.getTime() + offsetMs));
    }

    public static FoodListing listing(String id, DistributionType type, int total, int available, Date now) {
        FoodListing l = new FoodListing();
        l.setListingId(id);
        l.setProviderId("provider1");
        l.setProviderName("Green Bakery");
        l.setTitle("Listing " + id);
        l.setDescription("Fresh food " + id);
        l.setCategory(FoodCategory.BAKERY);
        l.setUnit(QuantityUnit.PIECES);
        l.setDistributionType(type);
        l.setTotalQuantity(total);
        l.setAvailableQuantity(available);
        l.setStatus(available == 0 ? ListingStatus.FULLY_RESERVED : ListingStatus.AVAILABLE);
        l.setPreparationTime(at(now, -2 * HOUR));
        l.setPickupStartTime(at(now, -HOUR));
        l.setPickupDeadline(at(now, 3 * HOUR));
        l.setCreatedAt(at(now, -HOUR));
        if (type == DistributionType.DISCOUNTED) {
            l.setOriginalPrice(100);
            l.setDiscountedPrice(40);
        }
        return l;
    }

    public static Reservation reservation(String id, ReservationStatus status, int qty, FoodCategory category,
                                          String receiverId, UserRole receiverRole, DistributionType type,
                                          Timestamp collectedAt) {
        Reservation r = new Reservation();
        r.setReservationId(id);
        r.setListingId("l-" + id);
        r.setProviderId("provider1");
        r.setReceiverId(receiverId);
        r.setReceiverRole(receiverRole);
        r.setQuantity(qty);
        r.setCategory(category);
        r.setDistributionType(type);
        r.setStatus(status);
        r.setCollectedAt(collectedAt);
        return r;
    }
}
