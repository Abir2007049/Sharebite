package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.sharebite.app.data.model.CharityVerification;
import com.sharebite.app.data.model.ProviderType;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserFactory;
import com.sharebite.app.data.model.VerificationStatus;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.UserRepository;

import java.util.HashMap;
import java.util.Map;

/** users/{uid} and charityVerifications/{uid}. */
public class FirestoreUserRepository extends BaseFirestoreRepository<User> implements UserRepository {

    public static final String USERS = "users";
    public static final String VERIFICATIONS = "charityVerifications";

    public FirestoreUserRepository(@NonNull FirebaseFirestore db) {
        super(db, USERS, User.class);
    }

    /** The collection mixes Provider/Receiver/Charity documents; pick the class by role. */
    @Nullable
    @Override
    protected User map(@NonNull DocumentSnapshot snapshot) {
        return UserFactory.fromSnapshot(snapshot);
    }

    @Override
    public void createProfile(User user, ResultCallback<Void> callback) {
        deliverVoid(collection.document(user.getUid()).set(user), callback);
    }

    @Override
    public void updateProfile(String uid, String fullName, String phone, String organizationName,
                              ProviderType providerType, ResultCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("phone", phone);
        updates.put("organizationName", organizationName);
        if (providerType != null) updates.put("providerType", providerType.name());
        deliverVoid(collection.document(uid).update(updates), callback);
    }

    @Override
    public void updateStorefront(String uid, String bio, String logoUrl, String coverUrl, ResultCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("bio", bio == null || bio.trim().isEmpty() ? null : bio.trim());
        if (logoUrl != null) updates.put("profileImageUrl", logoUrl);
        if (coverUrl != null) updates.put("coverImageUrl", coverUrl);
        deliverVoid(collection.document(uid).update(updates), callback);
    }

    @Override
    public void submitCharityVerification(CharityVerification verification, ResultCallback<Void> callback) {
        verification.setVerificationStatus(VerificationStatus.PENDING);
        verification.setSubmittedAt(Timestamp.now());
        verification.setReviewedAt(null);
        verification.setReviewerNote(null);

        DocumentReference verificationRef = db.collection(VERIFICATIONS).document(verification.getOrganizationId());
        DocumentReference userRef = collection.document(verification.getOrganizationId());
        WriteBatch batch = db.batch();
        batch.set(verificationRef, verification);
        batch.update(userRef, "verificationStatus", VerificationStatus.PENDING.name());
        deliverVoid(batch.commit(), callback);
    }

    @Override
    public void getCharityVerification(String organizationId, ResultCallback<CharityVerification> callback) {
        db.collection(VERIFICATIONS).document(organizationId).get()
                .addOnSuccessListener(snapshot -> {
                    CharityVerification v = snapshot.exists() ? snapshot.toObject(CharityVerification.class) : null;
                    if (v != null) v.assignDocumentId(snapshot.getId());
                    callback.onSuccess(v);
                })
                .addOnFailureListener(callback::onError);
    }
}
