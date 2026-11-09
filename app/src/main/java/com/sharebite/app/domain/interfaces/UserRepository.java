package com.sharebite.app.domain.interfaces;

import com.sharebite.app.data.model.CharityVerification;
import com.sharebite.app.data.model.ProviderType;
import com.sharebite.app.data.model.User;

/** Profiles stored in users/{uid} and charity verification submissions. */
public interface UserRepository extends Repository<User, String> {

    void createProfile(User user, ResultCallback<Void> callback);

    /** Updates only the user-editable profile fields; role and verification are not editable. */
    void updateProfile(String uid, String fullName, String phone, String organizationName,
                       ProviderType providerType, ResultCallback<Void> callback);

    /** Writes the submission and moves the charity's status to PENDING in one batch. */
    /** Provider storefront: logo (profile image), cover photo and about text. Null URLs are left unchanged. */
    void updateStorefront(String uid, @androidx.annotation.Nullable String bio, @androidx.annotation.Nullable String logoUrl,
                          @androidx.annotation.Nullable String coverUrl, ResultCallback<Void> callback);

    void submitCharityVerification(CharityVerification verification, ResultCallback<Void> callback);

    void getCharityVerification(String organizationId, ResultCallback<CharityVerification> callback);
}
