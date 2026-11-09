package com.sharebite.app.data.model;

/** Restaurants, bakeries, campus canteens and households that publish surplus food. */
public class Provider extends User {

    private ProviderType providerType = ProviderType.OTHER;
    /** Storefront cover photo (logo is {@code profileImageUrl}). */
    private String coverImageUrl;
    /** Short "about us" text on the storefront. */
    private String bio;

    public Provider() {
        // Required by Firestore.
    }

    public Provider(String uid, String fullName, String email, String phone,
                    String organizationName, ProviderType providerType) {
        super(uid, fullName, email, phone, organizationName, VerificationStatus.NOT_REQUIRED);
        this.providerType = providerType == null ? ProviderType.OTHER : providerType;
    }

    @Override
    public UserRole getRole() {
        return UserRole.PROVIDER;
    }

    /** Providers share food; they do not reserve it. */
    @Override
    public boolean canReserve(DistributionType type) {
        return false;
    }

    @Override
    protected int perReservationLimit(FoodListing listing) {
        return 0;
    }

    @Override
    public boolean canCreateListings() {
        return true;
    }

    /** Businesses are shown by their trading name; households by the person's name. */
    @Override
    public String displayName() {
        if (providerType.isBusiness() && hasText(getOrganizationName())) {
            return getOrganizationName();
        }
        return getFullName();
    }

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public ProviderType getProviderType() { return providerType; }
    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType == null ? ProviderType.OTHER : providerType;
    }
}
