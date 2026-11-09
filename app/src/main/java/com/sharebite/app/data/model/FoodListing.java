package com.sharebite.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.Locale;

/**
 * A surplus food listing published by a provider.
 *
 * <p>Quantity changes go through {@link #reserve(int, Date)} and {@link #release(int)} so the
 * invariants (0 ≤ available ≤ total, status follows stock) live in one place. The repository
 * calls these inside a Firestore transaction; firestore.rules re-checks them server-side.
 */
public class FoodListing implements FirestoreDocument {

    private String listingId;
    private String providerId;
    private String providerName;
    private ProviderType providerType;
    private String title;
    private String description;
    private FoodCategory category;
    private String imageUrl;
    private int totalQuantity;
    private int availableQuantity;
    private QuantityUnit unit;
    private DistributionType distributionType;
    private double originalPrice;
    private double discountedPrice;
    private Timestamp preparationTime;
    private Timestamp pickupStartTime;
    private Timestamp pickupDeadline;
    private String pickupAddress;
    @Nullable private Double latitude;
    @Nullable private Double longitude;
    private String storageInformation;
    private String allergens;
    private String foodSafetyNotes;
    /** Provider's explicit declaration that the food is fit for human consumption. */
    private boolean safetyDeclared;
    private ListingStatus status;
    /** ID of the reservation that last changed availableQuantity; lets security rules verify the change. */
    private String lastReservationId;
    /** Provider logo copied onto the listing (like providerName) so cards can show it. */
    private String providerLogoUrl;
    /** Photo credit when the picture needs attribution (e.g. a Creative Commons photo). */
    private String imageCredit;
    @ServerTimestamp private Timestamp createdAt;
    @ServerTimestamp private Timestamp updatedAt;

    public FoodListing() {
        // Required by Firestore.
    }

    // ---- Domain behaviour ------------------------------------------------------------------

    /** Status as users should see it: an open listing past its deadline is expired. */
    @NonNull
    public ListingStatus effectiveStatus(@NonNull Date now) {
        ListingStatus stored = status == null ? ListingStatus.AVAILABLE : status;
        if (stored.isOpen() && isPastDeadline(now)) return ListingStatus.EXPIRED;
        return stored;
    }

    public boolean isPastDeadline(@NonNull Date now) {
        return pickupDeadline == null || !now.before(pickupDeadline.toDate());
    }

    public boolean isReservableAt(@NonNull Date now) {
        return effectiveStatus(now) == ListingStatus.AVAILABLE && availableQuantity > 0;
    }

    /**
     * Validates and applies a reservation of {@code quantity} units.
     *
     * @throws BusinessRuleException if the listing cannot supply that quantity right now
     */
    public void reserve(int quantity, @NonNull Date now) throws BusinessRuleException {
        if (quantity <= 0) {
            throw new BusinessRuleException(BusinessRuleException.Reason.INVALID_QUANTITY, "Quantity must be at least 1.");
        }
        if (isPastDeadline(now)) {
            throw new BusinessRuleException(BusinessRuleException.Reason.LISTING_EXPIRED, "The pickup deadline for this listing has passed.");
        }
        if (status != ListingStatus.AVAILABLE) {
            throw new BusinessRuleException(BusinessRuleException.Reason.LISTING_NOT_AVAILABLE, "This listing is no longer available.");
        }
        if (quantity > availableQuantity) {
            throw new BusinessRuleException(BusinessRuleException.Reason.INSUFFICIENT_QUANTITY,
                    String.format(Locale.US, "Only %d left — please reserve a smaller quantity.", availableQuantity));
        }
        availableQuantity -= quantity;
        if (availableQuantity == 0) status = ListingStatus.FULLY_RESERVED;
    }

    /** Returns quantity from a cancelled/rejected reservation to the listing. */
    public void release(int quantity) {
        if (quantity <= 0) return;
        availableQuantity = Math.min(totalQuantity, availableQuantity + quantity);
        if (status == ListingStatus.FULLY_RESERVED && availableQuantity > 0) {
            status = ListingStatus.AVAILABLE;
        }
    }

    /**
     * Changes the total quantity when the provider edits a listing. Units already reserved are
     * preserved, so the new total cannot be lower than the reserved amount.
     */
    public void changeTotalQuantity(int newTotal) throws BusinessRuleException {
        int reserved = totalQuantity - availableQuantity;
        if (newTotal < Math.max(1, reserved)) {
            throw new BusinessRuleException(BusinessRuleException.Reason.INVALID_QUANTITY,
                    String.format(Locale.US, "%d units are already reserved — the total cannot be lower than that.", reserved));
        }
        totalQuantity = newTotal;
        availableQuantity = newTotal - reserved;
        if (status == ListingStatus.FULLY_RESERVED && availableQuantity > 0) status = ListingStatus.AVAILABLE;
        if (status == ListingStatus.AVAILABLE && availableQuantity == 0) status = ListingStatus.FULLY_RESERVED;
    }

    public int reservedQuantity() {
        return totalQuantity - availableQuantity;
    }

    /** Price per unit the receiver pays (0 for free and charity listings). */
    public double unitPrice() {
        return distributionType == DistributionType.DISCOUNTED ? discountedPrice : 0d;
    }

    public int discountPercent() {
        if (distributionType != DistributionType.DISCOUNTED || originalPrice <= 0) return 0;
        return (int) Math.round((1 - discountedPrice / originalPrice) * 100);
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /** True unless the provider explicitly declared that no common allergens are present. */
    public boolean hasDeclaredAllergens() {
        if (allergens == null) return false;
        String a = allergens.trim().toLowerCase(Locale.ROOT);
        return !a.isEmpty() && !a.equals("none") && !a.equals("none declared") && !a.equals("no allergens");
    }

    public boolean isOwnedBy(@Nullable String uid) {
        return uid != null && uid.equals(providerId);
    }

    // ---- Bean accessors --------------------------------------------------------------------

    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public ProviderType getProviderType() { return providerType; }
    public void setProviderType(ProviderType providerType) { this.providerType = providerType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public FoodCategory getCategory() { return category; }
    public void setCategory(FoodCategory category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }

    public int getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }

    public QuantityUnit getUnit() { return unit; }
    public void setUnit(QuantityUnit unit) { this.unit = unit; }

    public DistributionType getDistributionType() { return distributionType; }
    public void setDistributionType(DistributionType distributionType) { this.distributionType = distributionType; }

    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public double getDiscountedPrice() { return discountedPrice; }
    public void setDiscountedPrice(double discountedPrice) { this.discountedPrice = discountedPrice; }

    public Timestamp getPreparationTime() { return preparationTime; }
    public void setPreparationTime(Timestamp preparationTime) { this.preparationTime = preparationTime; }

    public Timestamp getPickupStartTime() { return pickupStartTime; }
    public void setPickupStartTime(Timestamp pickupStartTime) { this.pickupStartTime = pickupStartTime; }

    public Timestamp getPickupDeadline() { return pickupDeadline; }
    public void setPickupDeadline(Timestamp pickupDeadline) { this.pickupDeadline = pickupDeadline; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    @Nullable public Double getLatitude() { return latitude; }
    public void setLatitude(@Nullable Double latitude) { this.latitude = latitude; }

    @Nullable public Double getLongitude() { return longitude; }
    public void setLongitude(@Nullable Double longitude) { this.longitude = longitude; }

    public String getStorageInformation() { return storageInformation; }
    public void setStorageInformation(String storageInformation) { this.storageInformation = storageInformation; }

    public String getAllergens() { return allergens; }
    public void setAllergens(String allergens) { this.allergens = allergens; }

    public String getFoodSafetyNotes() { return foodSafetyNotes; }
    public void setFoodSafetyNotes(String foodSafetyNotes) { this.foodSafetyNotes = foodSafetyNotes; }

    public boolean isSafetyDeclared() { return safetyDeclared; }
    public void setSafetyDeclared(boolean safetyDeclared) { this.safetyDeclared = safetyDeclared; }

    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }

    public String getProviderLogoUrl() { return providerLogoUrl; }
    public void setProviderLogoUrl(String providerLogoUrl) { this.providerLogoUrl = providerLogoUrl; }

    public String getImageCredit() { return imageCredit; }
    public void setImageCredit(String imageCredit) { this.imageCredit = imageCredit; }

    public String getLastReservationId() { return lastReservationId; }
    public void setLastReservationId(String lastReservationId) { this.lastReservationId = lastReservationId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String documentId() { return listingId; }

    @Override
    public void assignDocumentId(String id) { this.listingId = id; }
}
