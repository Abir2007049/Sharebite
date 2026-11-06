package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.sharebite.app.data.model.BusinessRuleException;
import com.sharebite.app.data.model.BusinessRuleException.Reason;
import com.sharebite.app.data.model.Charity;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.NotificationType;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.ReservationRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reservation workflow on Firestore. Each operation is a transaction that:
 * <ol>
 *     <li>re-reads the reservation and/or listing (so decisions use the latest data),</li>
 *     <li>applies the change through the model's rule methods (which throw on violations),</li>
 *     <li>writes the reservation, the listing quantity and a notification atomically.</li>
 * </ol>
 * Two users reserving the last portions at the same moment therefore cannot both succeed:
 * Firestore retries the losing transaction against the updated stock, where it then fails.
 */
public class FirestoreReservationRepository extends BaseFirestoreRepository<Reservation> implements ReservationRepository {

    public static final String COLLECTION = "reservations";

    private final NotificationWriter notifications;

    public FirestoreReservationRepository(@NonNull FirebaseFirestore db) {
        super(db, COLLECTION, Reservation.class);
        this.notifications = new NotificationWriter(db);
    }

    private DocumentReference listingRef(String listingId) {
        return db.collection(FirestoreFoodRepository.COLLECTION).document(listingId);
    }

    @Override
    public void reserve(String listingId, User receiver, int quantity, ResultCallback<Reservation> callback) {
        DocumentReference reservationRef = collection.document();
        db.runTransaction((Transaction.Function<Reservation>) tx -> {
            DocumentSnapshot listingSnap = tx.get(listingRef(listingId));
            FoodListing listing = listingSnap.toObject(FoodListing.class);
            if (listing == null) throw TransactionAbort.of(Reason.NOT_FOUND, "This listing no longer exists.");
            listing.assignDocumentId(listingSnap.getId());

            if (listing.isOwnedBy(receiver.getUid())) {
                throw TransactionAbort.of(Reason.NOT_PERMITTED, "You cannot reserve your own listing.");
            }
            if (!receiver.canReserve(listing.getDistributionType())) {
                if (listing.getDistributionType() == DistributionType.CHARITY && receiver instanceof Charity) {
                    throw TransactionAbort.of(Reason.VERIFICATION_REQUIRED,
                            "Charity donations can be reserved once your organisation is verified.");
                }
                throw TransactionAbort.of(Reason.NOT_PERMITTED, "Your account type cannot reserve this listing.");
            }
            int max = receiver.maxReservableQuantity(listing);
            if (quantity > max && max > 0) {
                throw TransactionAbort.of(Reason.INVALID_QUANTITY, "You can reserve at most " + max + " from this listing.");
            }
            Timestamp now = Timestamp.now();
            try {
                listing.reserve(quantity, now.toDate());
            } catch (BusinessRuleException e) {
                throw new TransactionAbort(e);
            }

            Reservation reservation = Reservation.create(reservationRef.getId(), listing, receiver, quantity, now);
            tx.set(reservationRef, reservation);

            Map<String, Object> listingUpdate = new HashMap<>();
            listingUpdate.put("availableQuantity", listing.getAvailableQuantity());
            listingUpdate.put("status", listing.getStatus().name());
            listingUpdate.put("lastReservationId", reservation.getReservationId());
            listingUpdate.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(listingRef(listingId), listingUpdate);

            notifications.write(tx, notifications.forEvent(NotificationType.RESERVATION_REQUEST, reservation, receiver.getUid()));
            return reservation;
        }).addOnSuccessListener(callback::onSuccess).addOnFailureListener(callback::onError);
    }

    @Override
    public void confirm(Reservation reservation, User provider, ResultCallback<Void> callback) {
        DocumentReference ref = collection.document(reservation.getReservationId());
        db.runTransaction((Transaction.Function<Void>) tx -> {
            Reservation current = readOwned(tx, ref, provider.getUid(), true);
            DocumentSnapshot listingSnap = tx.get(listingRef(current.getListingId()));
            FoodListing listing = listingSnap.toObject(FoodListing.class);
            if (listing != null && listing.isPastDeadline(new java.util.Date())) {
                throw TransactionAbort.of(Reason.LISTING_EXPIRED, "The pickup deadline has passed; decline this request instead.");
            }
            Timestamp now = Timestamp.now();
            transition(current, ReservationStatus.CONFIRMED, now, null);
            tx.update(ref, "status", ReservationStatus.CONFIRMED.name(), "confirmedAt", now);
            notifications.write(tx, notifications.forEvent(NotificationType.RESERVATION_CONFIRMED, current, provider.getUid()));
            return null;
        }).addOnSuccessListener(v -> callback.onSuccess(null)).addOnFailureListener(callback::onError);
    }

    @Override
    public void reject(Reservation reservation, User provider, ResultCallback<Void> callback) {
        DocumentReference ref = collection.document(reservation.getReservationId());
        db.runTransaction((Transaction.Function<Void>) tx -> {
            Reservation current = readOwned(tx, ref, provider.getUid(), true);
            DocumentReference lRef = listingRef(current.getListingId());
            DocumentSnapshot listingSnap = tx.get(lRef);

            Timestamp now = Timestamp.now();
            transition(current, ReservationStatus.REJECTED, now, null);
            tx.update(ref, "status", ReservationStatus.REJECTED.name(), "rejectedAt", now);
            releaseQuantity(tx, lRef, listingSnap, current, false);
            notifications.write(tx, notifications.forEvent(NotificationType.RESERVATION_REJECTED, current, provider.getUid()));
            return null;
        }).addOnSuccessListener(v -> callback.onSuccess(null)).addOnFailureListener(callback::onError);
    }

    @Override
    public void cancel(Reservation reservation, User actor, ResultCallback<Void> callback) {
        DocumentReference ref = collection.document(reservation.getReservationId());
        db.runTransaction((Transaction.Function<Void>) tx -> {
            DocumentSnapshot snap = tx.get(ref);
            Reservation current = snap.toObject(Reservation.class);
            if (current == null) throw TransactionAbort.of(Reason.NOT_FOUND, "Reservation not found.");
            current.assignDocumentId(snap.getId());
            if (!current.involves(actor.getUid())) {
                throw TransactionAbort.of(Reason.NOT_PERMITTED, "You cannot change this reservation.");
            }
            boolean byReceiver = actor.getUid().equals(current.getReceiverId());
            DocumentReference lRef = listingRef(current.getListingId());
            DocumentSnapshot listingSnap = tx.get(lRef);

            Timestamp now = Timestamp.now();
            transition(current, ReservationStatus.CANCELLED, now, null);
            tx.update(ref, "status", ReservationStatus.CANCELLED.name(), "cancelledAt", now);
            // Receivers may only touch the listing through the rule-checked "release" path,
            // which requires lastReservationId to point at this reservation.
            releaseQuantity(tx, lRef, listingSnap, current, byReceiver);
            notifications.write(tx, notifications.forEvent(NotificationType.RESERVATION_CANCELLED, current, actor.getUid()));
            return null;
        }).addOnSuccessListener(v -> callback.onSuccess(null)).addOnFailureListener(callback::onError);
    }

    @Override
    public void markCollected(Reservation reservation, User actor, ResultCallback<Void> callback) {
        DocumentReference ref = collection.document(reservation.getReservationId());
        db.runTransaction((Transaction.Function<Void>) tx -> {
            DocumentSnapshot snap = tx.get(ref);
            Reservation current = snap.toObject(Reservation.class);
            if (current == null) throw TransactionAbort.of(Reason.NOT_FOUND, "Reservation not found.");
            current.assignDocumentId(snap.getId());
            if (!current.involves(actor.getUid())) {
                throw TransactionAbort.of(Reason.NOT_PERMITTED, "You cannot change this reservation.");
            }
            Reservation.CollectionConfirmer confirmer = actor.getUid().equals(current.getProviderId())
                    ? Reservation.CollectionConfirmer.PROVIDER : Reservation.CollectionConfirmer.RECEIVER;
            Timestamp now = Timestamp.now();
            // Throws if already COLLECTED — prevents double collection.
            transition(current, ReservationStatus.COLLECTED, now, confirmer);
            tx.update(ref, "status", ReservationStatus.COLLECTED.name(), "collectedAt", now,
                    "collectionConfirmedBy", confirmer.name());
            notifications.write(tx, notifications.forEvent(NotificationType.COLLECTION_COMPLETED, current, actor.getUid()));
            return null;
        }).addOnSuccessListener(v -> callback.onSuccess(null)).addOnFailureListener(callback::onError);
    }

    @Override
    public Subscription observeForReceiver(String receiverId, ResultCallback<List<Reservation>> callback) {
        return observeQuery(collection.whereEqualTo("receiverId", receiverId), sorted(callback));
    }

    @Override
    public Subscription observeForProvider(String providerId, ResultCallback<List<Reservation>> callback) {
        return observeQuery(collection.whereEqualTo("providerId", providerId), sorted(callback));
    }

    @Override
    public void getForListing(String listingId, String providerId, ResultCallback<List<Reservation>> callback) {
        // The providerId filter lets security rules prove the caller may read every result.
        getQuery(collection.whereEqualTo("listingId", listingId).whereEqualTo("providerId", providerId), sorted(callback));
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Reads a reservation inside the transaction and checks that {@code uid} owns it as provider. */
    private Reservation readOwned(Transaction tx, DocumentReference ref, String uid, boolean asProvider)
            throws com.google.firebase.firestore.FirebaseFirestoreException {
        DocumentSnapshot snap = tx.get(ref);
        Reservation current = snap.toObject(Reservation.class);
        if (current == null) throw TransactionAbort.of(Reason.NOT_FOUND, "Reservation not found.");
        current.assignDocumentId(snap.getId());
        String owner = asProvider ? current.getProviderId() : current.getReceiverId();
        if (!uid.equals(owner)) throw TransactionAbort.of(Reason.NOT_PERMITTED, "You cannot change this reservation.");
        return current;
    }

    private static void transition(Reservation r, ReservationStatus target, Timestamp at,
                                   @Nullable Reservation.CollectionConfirmer confirmer) {
        try {
            r.transitionTo(target, at, confirmer);
        } catch (BusinessRuleException e) {
            throw new TransactionAbort(e);
        }
    }

    /** Returns the reservation's units to the listing (if it still exists and is open). */
    private void releaseQuantity(Transaction tx, DocumentReference lRef, DocumentSnapshot listingSnap,
                                 Reservation reservation, boolean stampReservationId) {
        FoodListing listing = listingSnap.toObject(FoodListing.class);
        if (listing == null) return; // Listing was deleted; nothing to give back.
        listing.release(reservation.getQuantity());
        Map<String, Object> update = new HashMap<>();
        update.put("availableQuantity", listing.getAvailableQuantity());
        if (listing.getStatus() != null) update.put("status", listing.getStatus().name());
        update.put("updatedAt", FieldValue.serverTimestamp());
        if (stampReservationId) update.put("lastReservationId", reservation.getReservationId());
        tx.update(lRef, update);
    }

    private static ResultCallback<List<Reservation>> sorted(ResultCallback<List<Reservation>> callback) {
        return new ResultCallback<List<Reservation>>() {
            @Override
            public void onSuccess(List<Reservation> result) {
                List<Reservation> copy = new ArrayList<>(result);
                Collections.sort(copy, (a, b) -> Long.compare(millis(b.getReservedAt()), millis(a.getReservedAt())));
                callback.onSuccess(copy);
            }

            @Override
            public void onError(@NonNull Exception error) {
                callback.onError(error);
            }
        };
    }

    private static long millis(Timestamp t) {
        return t == null ? Long.MAX_VALUE : t.toDate().getTime();
    }
}
