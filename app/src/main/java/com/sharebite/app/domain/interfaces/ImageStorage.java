package com.sharebite.app.domain.interfaces;

/**
 * Binary uploads. Delivers a URL the app's {@code ImageLoader} can display: an https URL (Firebase
 * Storage) or a {@code firestore-image://} reference (images stored in Firestore when the project
 * has no Storage bucket, e.g. on the free Spark plan).
 */
public interface ImageStorage {

    /** What an image is for; decides who may write it and who may read it. */
    enum Purpose { LISTING, LOGO, COVER, VERIFICATION }

    void uploadListingImage(String providerId, String listingId, byte[] jpegBytes, ResultCallback<String> callback);

    void uploadVerificationDocument(String organizationId, byte[] jpegBytes, ResultCallback<String> callback);

    /** A provider logo / profile picture ({@link Purpose#LOGO}) or storefront cover ({@link Purpose#COVER}). */
    void uploadProfileImage(String uid, Purpose purpose, byte[] jpegBytes, ResultCallback<String> callback);

    /** Longest side, in pixels, that pictures are resized to before upload. */
    default int maxDimensionPx() { return 1280; }

    /** Largest upload accepted by this backend, in bytes. */
    default int maxBytes() { return 5 * 1024 * 1024; }
}
