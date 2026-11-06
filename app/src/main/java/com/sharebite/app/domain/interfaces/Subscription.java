package com.sharebite.app.domain.interfaces;

/**
 * Handle for a realtime listener. Owners (usually ViewModels) must call {@link #cancel()} when
 * they no longer need updates, e.g. in {@code onCleared()}, to avoid leaking Firestore listeners.
 */
public interface Subscription {

    void cancel();

    /** A subscription that holds nothing; useful as a safe default. */
    Subscription NONE = () -> { };
}
