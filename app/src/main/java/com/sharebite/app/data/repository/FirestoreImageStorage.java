package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sharebite.app.domain.interfaces.ImageStorage;
import com.sharebite.app.domain.interfaces.ResultCallback;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Stores compressed JPEGs as Firestore documents. Used when the Firebase project has no Storage
 * bucket (new projects need the paid Blaze plan for Storage), so photos still work on the free plan.
 *
 * <p>Public pictures (listing photos, logos, covers) live in {@code images/}; verification documents
 * in {@code privateImages/}, readable only by their owner and reviewers. Sizes are kept well under
 * Firestore's 1 MiB document limit, and firestore.rules enforces the same limits.
 */
public class FirestoreImageStorage implements ImageStorage {

    public static final String SCHEME = "firestore-image://";
    public static final String PUBLIC = "images";
    public static final String PRIVATE = "privateImages";
    /** Mirrors the limit in firestore.rules (750 KiB). */
    public static final int MAX_BYTES = 700 * 1024;

    private final FirebaseFirestore db;

    public FirestoreImageStorage(@NonNull FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void uploadListingImage(String providerId, String listingId, byte[] jpegBytes, ResultCallback<String> callback) {
        upload(PUBLIC, providerId, Purpose.LISTING, jpegBytes, callback);
    }

    @Override
    public void uploadVerificationDocument(String organizationId, byte[] jpegBytes, ResultCallback<String> callback) {
        upload(PRIVATE, organizationId, Purpose.VERIFICATION, jpegBytes, callback);
    }

    @Override
    public void uploadProfileImage(String uid, Purpose purpose, byte[] jpegBytes, ResultCallback<String> callback) {
        upload(PUBLIC, uid, purpose, jpegBytes, callback);
    }

    @Override
    public int maxDimensionPx() { return 1024; }

    @Override
    public int maxBytes() { return MAX_BYTES; }

    private void upload(String collection, String ownerId, Purpose purpose, byte[] bytes, ResultCallback<String> callback) {
        if (bytes.length > MAX_BYTES) {
            callback.onError(new IllegalArgumentException("This photo is too large to upload."));
            return;
        }
        DocumentReference ref = db.collection(collection).document();
        Map<String, Object> doc = new HashMap<>();
        doc.put("imageId", ref.getId());
        doc.put("ownerId", ownerId);
        doc.put("purpose", purpose.name().toLowerCase(Locale.ROOT));
        doc.put("contentType", "image/jpeg");
        doc.put("data", Blob.fromBytes(bytes));
        doc.put("createdAt", FieldValue.serverTimestamp());
        ref.set(doc)
                .addOnSuccessListener(v -> callback.onSuccess(SCHEME + collection + "/" + ref.getId()))
                .addOnFailureListener(callback::onError);
    }
}
