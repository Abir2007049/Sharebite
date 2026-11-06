package com.sharebite.app.data.model;

/**
 * Charitable organisations collecting food for community distribution. Registering as a charity
 * does not make an account verified: charity-only listings require {@link VerificationStatus#VERIFIED},
 * which can only be granted by a reviewer.
 */
public class Charity extends User {

    public Charity() {
        // Required by Firestore.
    }

    public Charity(String uid, String fullName, String email, String phone, String organizationName) {
        super(uid, fullName, email, phone, organizationName, VerificationStatus.UNVERIFIED);
    }

    @Override
    public UserRole getRole() {
        return UserRole.CHARITY;
    }

    public boolean hasVerifiedStatus() {
        return getVerificationStatus() == VerificationStatus.VERIFIED;
    }

    /** Free food is open to everyone; charity donations need a verified organisation. */
    @Override
    public boolean canReserve(DistributionType type) {
        if (type == DistributionType.FREE) return true;
        return type == DistributionType.CHARITY && hasVerifiedStatus();
    }

    /** Charities collect in bulk, limited only by the listing's stock. */
    @Override
    protected int perReservationLimit(FoodListing listing) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean requiresVerification() {
        return true;
    }

    @Override
    public String displayName() {
        return hasText(getOrganizationName()) ? getOrganizationName() : getFullName();
    }
}
