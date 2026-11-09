package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.sharebite.app.domain.interfaces.ImageStorage;
import com.sharebite.app.domain.interfaces.ResultCallback;

/** Uploads compressed JPEGs to Firebase Storage. Paths mirror storage.rules. */
public class FirebaseImageStorage implements ImageStorage {

    /** Supplies the Storage instance lazily. */
    public interface StorageProvider {
        FirebaseStorage get();
    }

    /**
     * Resolved lazily: a Firebase project without Storage enabled must still be able to run the
     * rest of the app (photos are optional), so a missing bucket surfaces as an upload error.
     */
    private final StorageProvider storageProvider;

    public FirebaseImageStorage(@NonNull StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    @Override
    public void uploadListingImage(String providerId, String listingId, byte[] jpegBytes, ResultCallback<String> callback) {
        // A timestamp suffix gives each edit a fresh URL so cached old images are not shown.
        upload("listing_images/" + providerId + "/" + listingId + "_" + System.currentTimeMillis() + ".jpg",
                jpegBytes, callback);
    }

    @Override
    public void uploadVerificationDocument(String organizationId, byte[] jpegBytes, ResultCallback<String> callback) {
        upload("verification_documents/" + organizationId + "/" + System.currentTimeMillis() + ".jpg",
                jpegBytes, callback);
    }

    @Override
    public void uploadProfileImage(String uid, Purpose purpose, byte[] jpegBytes, ResultCallback<String> callback) {
        upload("profile_images/" + uid + "/" + purpose.name().toLowerCase(java.util.Locale.ROOT) + "_"
                + System.currentTimeMillis() + ".jpg", jpegBytes, callback);
    }

    private void upload(String path, byte[] bytes, ResultCallback<String> callback) {
        StorageReference ref;
        try {
            ref = storageProvider.get().getReference(path);
        } catch (RuntimeException e) {
            callback.onError(new IllegalStateException(
                    "Firebase Storage is not set up for this project, so photos can't be uploaded. See README.", e));
            return;
        }
        StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
        ref.putBytes(bytes, metadata)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                .addOnFailureListener(callback::onError);
    }
}
