package com.sharebite.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.Timestamp;
import com.sharebite.app.TestData;

import org.junit.Test;

import java.util.Date;

public class ReservationTest {

    private final Date now = new Date();

    @Test
    public void stateMachineAllowsOnlyDocumentedTransitions() {
        assertTrue(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.CONFIRMED));
        assertTrue(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.REJECTED));
        assertTrue(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.CANCELLED));
        assertFalse(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.COLLECTED));
        assertTrue(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.COLLECTED));
        assertTrue(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.CANCELLED));
        assertFalse(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.REJECTED));
        for (ReservationStatus terminal : new ReservationStatus[]{
                ReservationStatus.COLLECTED, ReservationStatus.REJECTED, ReservationStatus.CANCELLED}) {
            for (ReservationStatus target : ReservationStatus.values()) {
                assertFalse(terminal + " -> " + target, terminal.canTransitionTo(target));
            }
        }
    }

    @Test
    public void createCopiesListingTermsAndPrice() {
        FoodListing l = TestData.listing("d", DistributionType.DISCOUNTED, 10, 10, now);
        Receiver alice = new Receiver("alice", "Alice", "alice@example.com", "+8801700000000");
        Reservation r = Reservation.create("r1", l, alice, 3, Timestamp.now());
        assertEquals(ReservationStatus.PENDING, r.getStatus());
        assertEquals("provider1", r.getProviderId());
        assertEquals("alice", r.getReceiverId());
        assertEquals(UserRole.RECEIVER, r.getReceiverRole());
        assertEquals(120.0, r.getTotalPrice(), 0.0001);
        assertEquals(DistributionType.DISCOUNTED, r.getDistributionType());
        assertTrue(r.holdsQuantity());
    }

    @Test
    public void collectingRecordsWhoConfirmedAndCannotRepeat() throws Exception {
        Reservation r = new Reservation();
        r.setStatus(ReservationStatus.CONFIRMED);
        r.transitionTo(ReservationStatus.COLLECTED, Timestamp.now(), Reservation.CollectionConfirmer.RECEIVER);
        assertEquals(ReservationStatus.COLLECTED, r.getStatus());
        assertNotNull(r.getCollectedAt());
        assertEquals(Reservation.CollectionConfirmer.RECEIVER, r.getCollectionConfirmedBy());
        assertFalse(r.holdsQuantity());
        try {
            r.transitionTo(ReservationStatus.COLLECTED, Timestamp.now(), Reservation.CollectionConfirmer.PROVIDER);
            fail("double collection must be rejected");
        } catch (BusinessRuleException e) {
            assertEquals(BusinessRuleException.Reason.INVALID_STATE, e.getReason());
        }
    }

    @Test
    public void involvesOnlyTheTwoParties() {
        Reservation r = new Reservation();
        r.setProviderId("p");
        r.setReceiverId("c");
        assertTrue(r.involves("p"));
        assertTrue(r.involves("c"));
        assertFalse(r.involves("x"));
        assertFalse(r.involves(null));
    }
}
