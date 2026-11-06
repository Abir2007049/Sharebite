package com.sharebite.app.domain.interfaces;

import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.User;

import java.util.List;

/**
 * Reservation workflow. Every state change runs in a Firestore transaction that re-reads the
 * current documents, so concurrent requests can neither oversell a listing nor process the same
 * reservation twice.
 */
public interface ReservationRepository extends Repository<Reservation, String> {

    void reserve(String listingId, User receiver, int quantity, ResultCallback<Reservation> callback);

    void confirm(Reservation reservation, User provider, ResultCallback<Void> callback);

    void reject(Reservation reservation, User provider, ResultCallback<Void> callback);

    void cancel(Reservation reservation, User receiver, ResultCallback<Void> callback);

    /** Either party confirms the hand-over; recorded with who confirmed it. */
    void markCollected(Reservation reservation, User actor, ResultCallback<Void> callback);

    Subscription observeForReceiver(String receiverId, ResultCallback<List<Reservation>> callback);

    Subscription observeForProvider(String providerId, ResultCallback<List<Reservation>> callback);

    /** Reservations of one listing (provider only). */
    void getForListing(String listingId, String providerId, ResultCallback<List<Reservation>> callback);
}
