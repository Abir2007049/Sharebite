package com.sharebite.app.data.model;

/** Individuals collecting free or discounted surplus food. */
public class Receiver extends User {

    public Receiver() {
        // Required by Firestore.
    }

    public Receiver(String uid, String fullName, String email, String phone) {
        super(uid, fullName, email, phone, null, VerificationStatus.NOT_REQUIRED);
    }

    @Override
    public UserRole getRole() {
        return UserRole.RECEIVER;
    }

    /** Charity-only listings are reserved for verified organisations. */
    @Override
    public boolean canReserve(DistributionType type) {
        return type == DistributionType.FREE || type == DistributionType.DISCOUNTED;
    }

    /** Individuals are capped per reservation so one person cannot claim an entire batch. */
    @Override
    protected int perReservationLimit(FoodListing listing) {
        return RECEIVER_MAX_PER_RESERVATION;
    }
}
