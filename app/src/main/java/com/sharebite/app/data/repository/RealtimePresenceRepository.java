package com.sharebite.app.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.sharebite.app.domain.interfaces.PresenceRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;

import java.util.HashMap;
import java.util.Map;

/**
 * Presence in Realtime Database at {@code /status/{uid}} = {state: online|offline, lastChanged},
 * and typing flags at {@code /typing/{reservationId}/{uid}}. Uses the documented
 * {@code .info/connected} + {@code onDisconnect()} pattern; see database.rules.json.
 */
public class RealtimePresenceRepository implements PresenceRepository {

    private static final String TAG = "Presence";
    @Nullable private final FirebaseDatabase db;
    private ValueEventListener connectedListener;
    private DatabaseReference connectedRef;

    /** @param db null when the project has no Realtime Database; every call is then a no-op. */
    public RealtimePresenceRepository(@Nullable FirebaseDatabase db) {
        this.db = db;
    }

    @Override
    public boolean isAvailable() {
        return db != null;
    }

    private static Map<String, Object> state(String state) {
        Map<String, Object> m = new HashMap<>();
        m.put("state", state);
        m.put("lastChanged", ServerValue.TIMESTAMP);
        return m;
    }

    @Override
    public void goOnline(String uid) {
        if (db == null || uid == null) return;
        goOffline(null); // drop any previous listener
        DatabaseReference status = db.getReference("status").child(uid);
        connectedRef = db.getReference(".info/connected");
        connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!Boolean.TRUE.equals(snap.getValue(Boolean.class))) return;
                // Server-side: if this client disappears, the server writes "offline" for us.
                status.onDisconnect().setValue(state("offline"))
                        .addOnSuccessListener(v -> status.setValue(state("online")))
                        .addOnFailureListener(e -> Log.w(TAG, "presence unavailable: " + e.getMessage()));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        connectedRef.addValueEventListener(connectedListener);
    }

    @Override
    public void goOffline(@Nullable String uid) {
        if (db == null) return;
        if (connectedRef != null && connectedListener != null) connectedRef.removeEventListener(connectedListener);
        connectedListener = null;
        if (uid != null) db.getReference("status").child(uid).setValue(state("offline"));
    }

    @Override
    public Subscription observe(String uid, ResultCallback<Presence> callback) {
        if (db == null) return Subscription.NONE;
        DatabaseReference ref = db.getReference("status").child(uid);
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                String s = snap.child("state").getValue(String.class);
                Long at = snap.child("lastChanged").getValue(Long.class);
                callback.onSuccess(new Presence("online".equals(s), at == null ? 0 : at));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.toException());
            }
        };
        ref.addValueEventListener(l);
        return () -> ref.removeEventListener(l);
    }

    @Override
    public void setTyping(String reservationId, String uid, boolean typing) {
        if (db == null) return;
        DatabaseReference ref = db.getReference("typing").child(reservationId).child(uid);
        if (typing) {
            ref.onDisconnect().removeValue();
            ref.setValue(true);
        } else {
            ref.removeValue();
        }
    }

    @Override
    public Subscription observeTyping(String reservationId, String otherUid, ResultCallback<Boolean> callback) {
        if (db == null) return Subscription.NONE;
        DatabaseReference ref = db.getReference("typing").child(reservationId).child(otherUid);
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                callback.onSuccess(Boolean.TRUE.equals(snap.getValue(Boolean.class)));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.toException());
            }
        };
        ref.addValueEventListener(l);
        return () -> ref.removeEventListener(l);
    }
}
