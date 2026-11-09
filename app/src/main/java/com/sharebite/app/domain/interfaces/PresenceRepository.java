package com.sharebite.app.domain.interfaces;

/**
 * Live presence and typing indicators, backed by Firebase Realtime Database (its onDisconnect
 * handlers mark a user offline even if the app is killed or loses its connection).
 */
public interface PresenceRepository {

    /** Someone's presence: online now, or when they were last seen (epoch millis, 0 if unknown). */
    final class Presence {
        public final boolean online;
        public final long lastChanged;

        public Presence(boolean online, long lastChanged) {
            this.online = online;
            this.lastChanged = lastChanged;
        }
    }

    /** Marks the signed-in user online while the app is visible; offline on {@link #goOffline}. */
    void goOnline(String uid);

    void goOffline(String uid);

    Subscription observe(String uid, ResultCallback<Presence> callback);

    void setTyping(String reservationId, String uid, boolean typing);

    Subscription observeTyping(String reservationId, String otherUid, ResultCallback<Boolean> callback);

    /** False when no Realtime Database is configured; callers then simply hide presence. */
    boolean isAvailable();
}
