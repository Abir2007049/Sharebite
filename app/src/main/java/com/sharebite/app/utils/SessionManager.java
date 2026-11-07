package com.sharebite.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.sharebite.app.data.model.Receiver;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;
import com.sharebite.app.domain.interfaces.UserRepository;

/**
 * Holds the signed-in user's profile for the whole app and keeps it live: a realtime listener on
 * users/{uid} means e.g. a charity sees its verification status change without restarting.
 */
public final class SessionManager {

    private final FirebaseAuth auth;
    private final UserRepository users;
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();
    private Subscription profileSubscription = Subscription.NONE;
    private String observedUid;
    private boolean guest;

    SessionManager(@NonNull FirebaseAuth auth, @NonNull UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    @Nullable
    public String uid() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }

    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    /** True while browsing without an account. A guest has no uid and never touches user data. */
    public boolean isGuest() {
        return guest;
    }

    /**
     * Starts browsing without an account. The placeholder profile is never stored anywhere; it only
     * lets the receiver-style screens render. Everything that needs an account checks {@link #isGuest()}.
     */
    public void startGuest() {
        profileSubscription.cancel();
        profileSubscription = Subscription.NONE;
        observedUid = null;
        guest = true;
        currentUser.setValue(new Receiver("", "Guest", "", ""));
    }

    public LiveData<User> currentUser() {
        return currentUser;
    }

    /** Latest loaded profile, or null if not loaded yet. */
    @Nullable
    public User user() {
        return currentUser.getValue();
    }

    /** Publishes a freshly loaded profile and starts listening for changes to it. */
    public void start(@NonNull User user) {
        guest = false;
        currentUser.setValue(user);
        if (user.getUid().equals(observedUid)) return;
        profileSubscription.cancel();
        observedUid = user.getUid();
        profileSubscription = users.observeById(user.getUid(), new ResultCallback<User>() {
            @Override
            public void onSuccess(User updated) {
                if (updated != null) currentUser.setValue(updated);
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Keep the last known profile; listener errors are typically transient (offline)
                // or happen right after sign-out when rules deny the read.
            }
        });
    }

    public void signOut() {
        guest = false;
        profileSubscription.cancel();
        profileSubscription = Subscription.NONE;
        observedUid = null;
        currentUser.setValue(null);
        auth.signOut();
    }
}
