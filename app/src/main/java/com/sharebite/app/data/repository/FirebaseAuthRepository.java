package com.sharebite.app.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.sharebite.app.data.model.BusinessRuleException;
import com.sharebite.app.data.model.RegistrationRequest;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.AuthRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.UserRepository;

/**
 * Email/password authentication with Firebase Auth. Session persistence is provided by
 * Firebase Auth itself (the signed-in user survives app restarts until {@link #logout()}).
 */
public class FirebaseAuthRepository implements AuthRepository {

    private static final String TAG = "AuthRepository";

    private final FirebaseAuth auth;
    private final UserRepository users;

    public FirebaseAuthRepository(@NonNull FirebaseAuth auth, @NonNull UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    @Override
    public void register(RegistrationRequest request, ResultCallback<User> callback) {
        auth.createUserWithEmailAndPassword(request.email, request.password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("Account creation returned no user."));
                        return;
                    }
                    User profile = request.toUser(firebaseUser.getUid());
                    firebaseUser.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(request.fullName).build());
                    users.createProfile(profile, new ResultCallback<Void>() {
                        @Override
                        public void onSuccess(Void ignored) {
                            callback.onSuccess(profile);
                        }

                        @Override
                        public void onError(@NonNull Exception error) {
                            // Roll back so the email is not stuck with an account that has no profile.
                            firebaseUser.delete().addOnCompleteListener(t -> {
                                if (!t.isSuccessful()) Log.w(TAG, "Could not roll back auth user", t.getException());
                                auth.signOut();
                                callback.onError(error);
                            });
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void login(String email, String password, ResultCallback<User> callback) {
        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("Sign-in returned no user."));
                        return;
                    }
                    users.getById(firebaseUser.getUid(), new ResultCallback<User>() {
                        @Override
                        public void onSuccess(User user) {
                            if (user == null) {
                                auth.signOut();
                                callback.onError(new BusinessRuleException(BusinessRuleException.Reason.NOT_FOUND,
                                        "No ShareBite profile exists for this account. Please register again."));
                            } else if (!user.isActive()) {
                                auth.signOut();
                                callback.onError(new BusinessRuleException(BusinessRuleException.Reason.NOT_PERMITTED,
                                        "This account has been deactivated."));
                            } else {
                                callback.onSuccess(user);
                            }
                        }

                        @Override
                        public void onError(@NonNull Exception error) {
                            auth.signOut();
                            callback.onError(error);
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void sendPasswordReset(String email, ResultCallback<Void> callback) {
        auth.sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Nullable
    @Override
    public String currentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @Override
    public void logout() {
        auth.signOut();
    }
}
