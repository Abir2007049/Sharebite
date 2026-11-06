package com.sharebite.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.util.Locale;

/**
 * A request by a receiver or charity to collect part of a listing. Listing details needed for
 * history screens (title, category, unit, address) are copied in, so history stays readable even
 * after the provider edits or removes the listing.
 */
public class Reservation implements FirestoreDocument {

    /** Who confirmed the hand-over when a reservation was marked collected. */
    public enum CollectionConfirmer { PROVIDER, RECEIVER }

    private String reservationId;
    private String listingId;
    private String listingTitle;
    private String listingImageUrl;
    private FoodCategory category;
    private QuantityUnit unit;
    private String providerId;
    private String providerName;
    private String receiverId;
    private String receiverName;
    private UserRole receiverRole;
    private String receiverPhone;
    private int quantity;
    private DistributionType distributionType;
    private double unitPrice;
    private double totalPrice;
    private String pickupAddress;
    private Timestamp pickupStartTime;
    private Timestamp pickupDeadline;
    private ReservationStatus status;
    private Timestamp reservedAt;
    private Timestamp confirmedAt;
    private Timestamp rejectedAt;
    private Timestamp cancelledAt;
    private Timestamp collectedAt;
    private CollectionConfirmer collectionConfirmedBy;

    public Reservation() {
        // Required by Firestore.
    }

    /** Builds a new PENDING reservation for {@code receiver} against {@code listing}. */
    @NonNull
    public static Reservation create(@NonNull String reservationId, @NonNull FoodListing listing,
                                     @NonNull User receiver, int quantity, @NonNull Timestamp now) {
        Reservation r = new Reservation();
        r.reservationId = reservationId;
        r.listingId = listing.getListingId();
        r.listingTitle = listing.getTitle();
        r.listingImageUrl = listing.getImageUrl();
        r.category = listing.getCategory();
        r.unit = listing.getUnit();
        r.providerId = listing.getProviderId();
        r.providerName = listing.getProviderName();
        r.receiverId = receiver.getUid();
        r.receiverName = receiver.displayName();
        r.receiverRole = receiver.getRole();
        r.receiverPhone = receiver.getPhone();
        r.quantity = quantity;
        r.distributionType = listing.getDistributionType();
        r.unitPrice = listing.unitPrice();
        r.totalPrice = quantity * listing.unitPrice();
        r.pickupAddress = listing.getPickupAddress();
        r.pickupStartTime = listing.getPickupStartTime();
        r.pickupDeadline = listing.getPickupDeadline();
        r.status = ReservationStatus.PENDING;
        r.reservedAt = now;
        return r;
    }

    // ---- Domain behaviour ------------------------------------------------------------------

    /**
     * Moves the reservation to {@code target} if the state machine allows it.
     *
     * @throws BusinessRuleException when the transition is invalid (e.g. already processed)
     */
    public void transitionTo(@NonNull ReservationStatus target, @NonNull Timestamp at,
                             @Nullable CollectionConfirmer confirmer) throws BusinessRuleException {
        ReservationStatus current = status == null ? ReservationStatus.PENDING : status;
        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleException(BusinessRuleException.Reason.INVALID_STATE,
                    "This reservation is already " + current.name().toLowerCase(Locale.ROOT) + " and cannot be changed to "
                            + target.name().toLowerCase(Locale.ROOT) + ".");
        }
        status = target;
        switch (target) {
            case CONFIRMED: confirmedAt = at; break;
            case REJECTED: rejectedAt = at; break;
            case CANCELLED: cancelledAt = at; break;
            case COLLECTED:
                collectedAt = at;
                collectionConfirmedBy = confirmer;
                break;
            default: break;
        }
    }

    public boolean involves(@Nullable String uid) {
        return uid != null && (uid.equals(providerId) || uid.equals(receiverId));
    }

    /** Whether this reservation still holds stock on the listing. */
    public boolean holdsQuantity() {
        return status != null && status.isActive();
    }

    // ---- Bean accessors --------------------------------------------------------------------

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }

    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }

    public String getListingImageUrl() { return listingImageUrl; }
    public void setListingImageUrl(String listingImageUrl) { this.listingImageUrl = listingImageUrl; }

    public FoodCategory getCategory() { return category; }
    public void setCategory(FoodCategory category) { this.category = category; }

    public QuantityUnit getUnit() { return unit; }
    public void setUnit(QuantityUnit unit) { this.unit = unit; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public UserRole getReceiverRole() { return receiverRole; }
    public void setReceiverRole(UserRole receiverRole) { this.receiverRole = receiverRole; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public DistributionType getDistributionType() { return distributionType; }
    public void setDistributionType(DistributionType distributionType) { this.distributionType = distributionType; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public Timestamp getPickupStartTime() { return pickupStartTime; }
    public void setPickupStartTime(Timestamp pickupStartTime) { this.pickupStartTime = pickupStartTime; }

    public Timestamp getPickupDeadline() { return pickupDeadline; }
    public void setPickupDeadline(Timestamp pickupDeadline) { this.pickupDeadline = pickupDeadline; }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public Timestamp getReservedAt() { return reservedAt; }
    public void setReservedAt(Timestamp reservedAt) { this.reservedAt = reservedAt; }

    public Timestamp getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Timestamp confirmedAt) { this.confirmedAt = confirmedAt; }

    public Timestamp getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(Timestamp rejectedAt) { this.rejectedAt = rejectedAt; }

    public Timestamp getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Timestamp cancelledAt) { this.cancelledAt = cancelledAt; }

    public Timestamp getCollectedAt() { return collectedAt; }
    public void setCollectedAt(Timestamp collectedAt) { this.collectedAt = collectedAt; }

    public CollectionConfirmer getCollectionConfirmedBy() { return collectionConfirmedBy; }
    public void setCollectionConfirmedBy(CollectionConfirmer collectionConfirmedBy) { this.collectionConfirmedBy = collectionConfirmedBy; }

    @Override
    public String documentId() { return reservationId; }

    @Override
    public void assignDocumentId(String id) { this.reservationId = id; }
}
