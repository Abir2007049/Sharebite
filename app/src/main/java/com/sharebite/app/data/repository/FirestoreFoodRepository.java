package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;
import com.sharebite.app.data.model.BusinessRuleException;
import com.sharebite.app.data.model.BusinessRuleException.Reason;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.data.model.NotificationType;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.domain.interfaces.FoodRepository;
import com.sharebite.app.domain.interfaces.ImageStorage;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** foodListings/{listingId}: CRUD plus provider lifecycle operations. */
public class FirestoreFoodRepository extends BaseFirestoreRepository<FoodListing> implements FoodRepository {

    public static final String COLLECTION = "foodListings";
    /** Attempts for operations that must see a stable set of reservations (see withActiveReservations). */
    private static final int MAX_ATTEMPTS = 3;

    private final ImageStorage images;
    private final NotificationWriter notifications;

    public FirestoreFoodRepository(@NonNull FirebaseFirestore db, @NonNull ImageStorage images) {
        super(db, COLLECTION, FoodListing.class);
        this.images = images;
        this.notifications = new NotificationWriter(db);
    }

    @Override
    public String newListingId() {
        return collection.document().getId();
    }

    @Override
    public void createListing(FoodListing listing, @Nullable byte[] imageBytes, ResultCallback<FoodListing> callback) {
        if (listing.getListingId() == null) listing.setListingId(newListingId());
        listing.setStatus(ListingStatus.AVAILABLE);
        listing.setAvailableQuantity(listing.getTotalQuantity());
        listing.setLastReservationId(null);
        listing.setCreatedAt(null); // Server timestamps.
        listing.setUpdatedAt(null);

        Runnable write = () -> collection.document(listing.getListingId()).set(listing)
                .addOnSuccessListener(v -> callback.onSuccess(listing))
                .addOnFailureListener(callback::onError);

        if (imageBytes == null) {
            write.run();
            return;
        }
        images.uploadListingImage(listing.getProviderId(), listing.getListingId(), imageBytes, new ResultCallback<String>() {
            @Override
            public void onSuccess(String url) {
                listing.setImageUrl(url);
                write.run();
            }

            @Override
            public void onError(@NonNull Exception error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void updateListing(FoodListing edited, @Nullable byte[] newImageBytes, ResultCallback<FoodListing> callback) {
        if (newImageBytes == null) {
            runUpdate(edited, callback);
            return;
        }
        images.uploadListingImage(edited.getProviderId(), edited.getListingId(), newImageBytes, new ResultCallback<String>() {
            @Override
            public void onSuccess(String url) {
                edited.setImageUrl(url);
                runUpdate(edited, callback);
            }

            @Override
            public void onError(@NonNull Exception error) {
                callback.onError(error);
            }
        });
    }

    private void runUpdate(FoodListing edited, ResultCallback<FoodListing> callback) {
        DocumentReference ref = collection.document(edited.getListingId());
        db.runTransaction((Transaction.Function<FoodListing>) tx -> {
            FoodListing current = readOwnedListing(tx, ref, edited.getProviderId());
            if (current.getStatus() == null || !current.getStatus().isOpen()) {
                throw TransactionAbort.of(Reason.INVALID_STATE, "Only active listings can be edited.");
            }
            if (current.reservedQuantity() > 0 && current.getDistributionType() != edited.getDistributionType()) {
                throw TransactionAbort.of(Reason.NOT_PERMITTED,
                        "The distribution type cannot change once food has been reserved.");
            }
            try {
                current.changeTotalQuantity(edited.getTotalQuantity());
            } catch (BusinessRuleException e) {
                throw new TransactionAbort(e);
            }

            Map<String, Object> u = new HashMap<>();
            u.put("title", edited.getTitle());
            u.put("description", edited.getDescription());
            u.put("category", edited.getCategory().name());
            u.put("imageUrl", edited.getImageUrl());
            u.put("unit", edited.getUnit().name());
            u.put("distributionType", edited.getDistributionType().name());
            u.put("originalPrice", edited.getOriginalPrice());
            u.put("discountedPrice", edited.getDiscountedPrice());
            u.put("preparationTime", edited.getPreparationTime());
            u.put("pickupStartTime", edited.getPickupStartTime());
            u.put("pickupDeadline", edited.getPickupDeadline());
            u.put("pickupAddress", edited.getPickupAddress());
            u.put("latitude", edited.getLatitude());
            u.put("longitude", edited.getLongitude());
            u.put("storageInformation", edited.getStorageInformation());
            u.put("allergens", edited.getAllergens());
            u.put("foodSafetyNotes", edited.getFoodSafetyNotes());
            u.put("safetyDeclared", edited.isSafetyDeclared());
            u.put("totalQuantity", current.getTotalQuantity());
            u.put("availableQuantity", current.getAvailableQuantity());
            u.put("status", current.getStatus().name());
            u.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ref, u);

            edited.setTotalQuantity(current.getTotalQuantity());
            edited.setAvailableQuantity(current.getAvailableQuantity());
            edited.setStatus(current.getStatus());
            return edited;
        }).addOnSuccessListener(callback::onSuccess).addOnFailureListener(callback::onError);
    }

    @Override
    public void cancelListing(FoodListing listing, ResultCallback<Void> callback) {
        withActiveReservations(listing, 1, callback, (tx, current, active) -> {
            Timestamp now = Timestamp.now();
            int released = 0;
            for (Reservation r : active) {
                // Pending requests are declined; confirmed ones are cancelled by the provider.
                ReservationStatus target = r.getStatus() == ReservationStatus.PENDING
                        ? ReservationStatus.REJECTED : ReservationStatus.CANCELLED;
                try {
                    r.transitionTo(target, now, null);
                } catch (BusinessRuleException e) {
                    throw new TransactionAbort(e);
                }
                DocumentReference rRef = db.collection(FirestoreReservationRepository.COLLECTION).document(r.getReservationId());
                tx.update(rRef, "status", target.name(), target == ReservationStatus.REJECTED ? "rejectedAt" : "cancelledAt", now);
                notifications.write(tx, notifications.forEvent(NotificationType.LISTING_CANCELLED, r, current.getProviderId()));
                released += r.getQuantity();
            }
            Map<String, Object> u = new HashMap<>();
            u.put("status", ListingStatus.CANCELLED.name());
            u.put("availableQuantity", Math.min(current.getTotalQuantity(), current.getAvailableQuantity() + released));
            u.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(collection.document(current.getListingId()), u);
        });
    }

    @Override
    public void markCompleted(FoodListing listing, ResultCallback<Void> callback) {
        withActiveReservations(listing, 1, callback, (tx, current, active) -> {
            if (!active.isEmpty()) {
                throw TransactionAbort.of(Reason.INVALID_STATE,
                        "Confirm, decline or complete the " + active.size() + " open reservation(s) before closing this listing.");
            }
            tx.update(collection.document(current.getListingId()),
                    "status", ListingStatus.COMPLETED.name(), "updatedAt", FieldValue.serverTimestamp());
        });
    }

    @Override
    public void deleteListing(FoodListing listing, ResultCallback<Void> callback) {
        db.collection(FirestoreReservationRepository.COLLECTION)
                .whereEqualTo("listingId", listing.getListingId())
                .whereEqualTo("providerId", listing.getProviderId())
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        callback.onError(new BusinessRuleException(Reason.NOT_PERMITTED,
                                "This listing has reservation history, so it can't be deleted. Withdraw it instead."));
                        return;
                    }
                    deliverVoid(collection.document(listing.getListingId()).delete(), callback);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void expireOverdueListings(String providerId, ResultCallback<Integer> callback) {
        collection.whereEqualTo("providerId", providerId).get()
                .addOnSuccessListener(snap -> {
                    Date now = new Date();
                    WriteBatch batch = db.batch();
                    int count = 0;
                    for (FoodListing l : mapAll(snap.getDocuments())) {
                        if (l.getStatus() != null && l.getStatus().isOpen() && l.isPastDeadline(now)) {
                            batch.update(collection.document(l.getListingId()),
                                    "status", ListingStatus.EXPIRED.name(), "updatedAt", FieldValue.serverTimestamp());
                            count++;
                        }
                    }
                    if (count == 0) {
                        callback.onSuccess(0);
                        return;
                    }
                    final int expired = count;
                    batch.commit().addOnSuccessListener(v -> callback.onSuccess(expired)).addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public Subscription observeAvailableListings(ResultCallback<List<FoodListing>> callback) {
        return observeQuery(collection.whereEqualTo("status", ListingStatus.AVAILABLE.name()), callback);
    }

    @Override
    public Subscription observeProviderListings(String providerId, ResultCallback<List<FoodListing>> callback) {
        return observeQuery(collection.whereEqualTo("providerId", providerId), callback);
    }

    @Override
    public Subscription observeAvailableFromProvider(String providerId, ResultCallback<List<FoodListing>> callback) {
        return observeQuery(collection.whereEqualTo("providerId", providerId)
                .whereEqualTo("status", ListingStatus.AVAILABLE.name()), callback);
    }

    @Override
    public void refreshProviderBranding(String providerId, String providerName, String logoUrl, ResultCallback<Void> callback) {
        collection.whereEqualTo("providerId", providerId).get().addOnSuccessListener(snap -> {
            com.google.firebase.firestore.WriteBatch batch = db.batch();
            int n = 0;
            for (DocumentSnapshot d : snap.getDocuments()) {
                String status = d.getString("status");
                if (!"AVAILABLE".equals(status) && !"FULLY_RESERVED".equals(status)) continue;
                Map<String, Object> u = new HashMap<>();
                u.put("providerName", providerName);
                u.put("providerLogoUrl", logoUrl);
                batch.update(d.getReference(), u);
                n++;
            }
            if (n == 0) callback.onSuccess(null);
            else deliverVoid(batch.commit(), callback);
        }).addOnFailureListener(callback::onError);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private FoodListing readOwnedListing(Transaction tx, DocumentReference ref, String providerId)
            throws com.google.firebase.firestore.FirebaseFirestoreException {
        DocumentSnapshot snap = tx.get(ref);
        FoodListing current = snap.toObject(FoodListing.class);
        if (current == null) throw TransactionAbort.of(Reason.NOT_FOUND, "This listing no longer exists.");
        current.assignDocumentId(snap.getId());
        if (!current.isOwnedBy(providerId)) throw TransactionAbort.of(Reason.NOT_PERMITTED, "You can only manage your own listings.");
        return current;
    }

    private interface ActiveReservationsWork {
        void apply(Transaction tx, FoodListing current, List<Reservation> active) throws com.google.firebase.firestore.FirebaseFirestoreException;
    }

    /**
     * Runs {@code work} in a transaction together with the listing's active reservations.
     *
     * <p>Firestore transactions cannot run queries, so the reservations are queried first and then
     * re-read inside the transaction. Every reservation create/cancel stamps the listing's
     * {@code lastReservationId}; if it changed between the query and the transaction, a
     * reservation was added or removed meanwhile, so the whole operation is retried.
     */
    private void withActiveReservations(FoodListing listing, int attempt, ResultCallback<Void> callback,
                                        ActiveReservationsWork work) {
        DocumentReference ref = collection.document(listing.getListingId());
        ref.get().addOnSuccessListener(listingSnap -> {
            String stamp = listingSnap.getString("lastReservationId");
            db.collection(FirestoreReservationRepository.COLLECTION)
                    .whereEqualTo("listingId", listing.getListingId())
                    .whereEqualTo("providerId", listing.getProviderId())
                    .get()
                    .addOnSuccessListener(snap -> {
                        List<String> activeIds = new ArrayList<>();
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            String s = d.getString("status");
                            if (ReservationStatus.PENDING.name().equals(s) || ReservationStatus.CONFIRMED.name().equals(s)) {
                                activeIds.add(d.getId());
                            }
                        }
                        db.runTransaction((Transaction.Function<Boolean>) tx -> {
                            FoodListing current = readOwnedListing(tx, ref, listing.getProviderId());
                            List<Reservation> active = new ArrayList<>();
                            for (String id : activeIds) {
                                DocumentSnapshot rs = tx.get(db.collection(FirestoreReservationRepository.COLLECTION).document(id));
                                Reservation r = rs.toObject(Reservation.class);
                                if (r != null && r.holdsQuantity()) {
                                    r.assignDocumentId(rs.getId());
                                    active.add(r);
                                }
                            }
                            if (!Objects.equals(stamp, current.getLastReservationId())) return false; // Retry.
                            ListingStatus st = current.getStatus();
                            if (st == null || (!st.isOpen() && st != ListingStatus.EXPIRED)) {
                                throw TransactionAbort.of(Reason.INVALID_STATE, "This listing is already closed.");
                            }
                            work.apply(tx, current, active);
                            return true;
                        }).addOnSuccessListener(done -> {
                            if (Boolean.TRUE.equals(done)) {
                                callback.onSuccess(null);
                            } else if (attempt < MAX_ATTEMPTS) {
                                withActiveReservations(listing, attempt + 1, callback, work);
                            } else {
                                callback.onError(new BusinessRuleException(Reason.INVALID_STATE,
                                        "Reservations are changing right now. Please try again in a moment."));
                            }
                        }).addOnFailureListener(callback::onError);
                    })
                    .addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }
}
