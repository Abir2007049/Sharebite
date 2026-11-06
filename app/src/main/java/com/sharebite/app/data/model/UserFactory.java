package com.sharebite.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

/** Creates the correct {@link User} subclass for a stored role. */
public final class UserFactory {

    private UserFactory() {
    }

    @NonNull
    public static Class<? extends User> classFor(@NonNull UserRole role) {
        switch (role) {
            case PROVIDER:
                return Provider.class;
            case CHARITY:
                return Charity.class;
            case RECEIVER:
            default:
                return Receiver.class;
        }
    }

    /** Maps a users/{uid} snapshot onto its concrete class, or null if missing/invalid. */
    @Nullable
    public static User fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        if (!snapshot.exists()) return null;
        UserRole role = UserRole.fromName(snapshot.getString("role"));
        if (role == null) return null;
        User user = snapshot.toObject(classFor(role));
        if (user != null) user.assignDocumentId(snapshot.getId());
        return user;
    }
}
