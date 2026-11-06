package com.sharebite.app.domain.interfaces;

import androidx.annotation.Nullable;

import com.sharebite.app.data.model.RegistrationRequest;
import com.sharebite.app.data.model.User;

/** Account lifecycle backed by Firebase Authentication. */
public interface AuthRepository {

    /** Creates the auth account and the users/{uid} profile document. */
    void register(RegistrationRequest request, ResultCallback<User> callback);

    void login(String email, String password, ResultCallback<User> callback);

    void sendPasswordReset(String email, ResultCallback<Void> callback);

    @Nullable
    String currentUserId();

    void logout();
}
