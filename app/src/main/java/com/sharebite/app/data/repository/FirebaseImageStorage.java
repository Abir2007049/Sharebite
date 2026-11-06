package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.sharebite.app.domain.interfaces.ImageStorage;
import com.sharebite.app.domain.interfaces.ResultCallback;

/** Uploads compressed JPEGs to Firebase Storage. Paths mirror storage.rules. */
public class FirebaseImageStorage implements ImageStorage {

    private final FirebaseStorage storage;

    public FirebaseImageStorage(@NonNull FirebaseStorage storage) {
        this.storage = storage;
    }

    @Override
    public void uploadListingImage(String providerId, String listingId, byte[] jpegBytes, ResultCallback<String> callback) {
        // A timestamp suffix gives each edit a fresh URL so cached old images are not shown.
        upload(storage.getReference("listing_images/" + providerId + "/" + listingId + "_" + System.currentTimeMillis() + ".jpg"),
                jpegBytes, callback);
    }

    @Override
    public void uploadVerificationDocument(String organizationId, byte[] jpegBytes, ResultCallback<String> callback) {
        upload(storage.getReference("verification_documents/" + organizationId + "/" + System.currentTimeMillis() + ".jpg"),
                jpegBytes, callback);
    }

    private void upload(StorageReference ref, byte[] bytes, ResultCallback<String> callback) {
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
