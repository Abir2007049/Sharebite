package com.sharebite.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Input collected by the registration screen. The password only travels to Firebase Auth and is
 * never written to Firestore.
 */
public final class RegistrationRequest {

    public final String fullName;
    public final String email;
    public final String password;
    public final String phone;
    public final UserRole role;
    @Nullable public final String organizationName;
    @Nullable public final ProviderType providerType;

    public RegistrationRequest(@NonNull String fullName, @NonNull String email, @NonNull String password,
                               @NonNull String phone, @NonNull UserRole role,
                               @Nullable String organizationName, @Nullable ProviderType providerType) {
        this.fullName = fullName.trim();
        this.email = email.trim();
        this.password = password;
        this.phone = phone.trim();
        this.role = role;
        this.organizationName = organizationName == null ? null : organizationName.trim();
        this.providerType = providerType;
    }

    /** Builds the profile for a newly created auth account (polymorphic construction by role). */
    @NonNull
    public User toUser(@NonNull String uid) {
        switch (role) {
            case PROVIDER:
                return new Provider(uid, fullName, email, phone, organizationName, providerType);
            case CHARITY:
                return new Charity(uid, fullName, email, phone, organizationName);
            case RECEIVER:
            default:
                return new Receiver(uid, fullName, email, phone);
        }
    }
}
