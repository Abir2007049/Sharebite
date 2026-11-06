package com.sharebite.app.domain.interfaces;

/** Binary uploads (Firebase Storage). Delivers a download URL. */
public interface ImageStorage {

    void uploadListingImage(String providerId, String listingId, byte[] jpegBytes, ResultCallback<String> callback);

    void uploadVerificationDocument(String organizationId, byte[] jpegBytes, ResultCallback<String> callback);
}
