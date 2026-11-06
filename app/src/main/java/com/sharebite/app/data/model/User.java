package com.sharebite.app.data.model;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * Base class for every ShareBite account. The concrete subclass (Provider, Receiver, Charity)
 * is chosen from the stored {@code role} by {@link UserFactory}; role-specific rules are expressed
 * by overriding the behaviour methods below rather than by switch statements in the UI.
 *
 * <p>Passwords are never part of this model — authentication is handled by Firebase Auth.
 */
public abstract class User implements FirestoreDocument {

    /** Individual receivers may hold at most this many units per reservation (fair sharing). */
    public static final int RECEIVER_MAX_PER_RESERVATION = 5;

    private String uid;
    private String fullName;
    private String email;
    private String phone;
    private String organizationName;
    private VerificationStatus verificationStatus;
    private String profileImageUrl;
    @ServerTimestamp private Timestamp createdAt;
    private boolean active = true;

    protected User() {
        // Required by Firestore.
    }

    protected User(String uid, String fullName, String email, String phone,
                   String organizationName, VerificationStatus verificationStatus) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.organizationName = organizationName;
        this.verificationStatus = verificationStatus;
    }

    // ---- Role-specific behaviour (polymorphism) -------------------------------------------

    /** The role is fixed by the concrete type. */
    public abstract UserRole getRole();

    /**
     * Firestore calls this while mapping the stored "role" field. The role is determined by the
     * concrete class, so the stored value is only checked for consistency.
     */
    public final void setRole(UserRole storedRole) {
        if (storedRole != null && storedRole != getRole()) {
            throw new IllegalStateException("Stored role " + storedRole + " does not match " + getClass().getSimpleName());
        }
    }

    /** Whether this account may reserve listings of the given distribution type at all. */
    public abstract boolean canReserve(DistributionType type);

    /** Upper bound for a single reservation on this listing, before stock is considered. */
    protected abstract int perReservationLimit(FoodListing listing);

    public boolean canCreateListings() {
        return false;
    }

    public boolean requiresVerification() {
        return false;
    }

    /** Name shown to other users (organisation name where it is meaningful). */
    public String displayName() {
        return fullName;
    }

    /**
     * Maximum quantity this user may reserve right now. Combines the role's own limit with the
     * listing's remaining stock; returns 0 when the user may not reserve the listing.
     */
    public final int maxReservableQuantity(@NonNull FoodListing listing) {
        if (!canReserve(listing.getDistributionType())) return 0;
        return Math.max(0, Math.min(listing.getAvailableQuantity(), perReservationLimit(listing)));
    }

    // ---- Bean accessors (Firestore mapping) ------------------------------------------------

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String documentId() { return uid; }

    @Override
    public void assignDocumentId(String id) { this.uid = id; }

    protected static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
