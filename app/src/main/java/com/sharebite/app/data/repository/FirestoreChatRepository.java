package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.sharebite.app.data.model.ChatMessage;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.ChatRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;

import java.util.ArrayList;
import java.util.List;

/** Firestore-backed chat in reservations/{id}/messages. Access is enforced by firestore.rules. */
public class FirestoreChatRepository implements ChatRepository {

    private static final int HISTORY_LIMIT = 200;
    private final FirebaseFirestore db;

    public FirestoreChatRepository(@NonNull FirebaseFirestore db) {
        this.db = db;
    }

    private CollectionReference messages(String reservationId) {
        return db.collection(FirestoreReservationRepository.COLLECTION).document(reservationId).collection("messages");
    }

    @Override
    public Subscription observe(String reservationId, ResultCallback<List<ChatMessage>> callback) {
        ListenerRegistration reg = messages(reservationId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(HISTORY_LIMIT)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snap == null) return;
                    List<ChatMessage> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ChatMessage m = d.toObject(ChatMessage.class, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                        if (m == null) continue;
                        m.assignDocumentId(d.getId());
                        out.add(m);
                    }
                    callback.onSuccess(out);
                });
        return reg::remove;
    }

    @Override
    public void send(String reservationId, User sender, String text, ResultCallback<Void> callback) {
        DocumentReference ref = messages(reservationId).document();
        ChatMessage m = new ChatMessage(ref.getId(), sender.getUid(), sender.displayName(), text);
        ref.set(m).addOnSuccessListener(v -> callback.onSuccess(null)).addOnFailureListener(callback::onError);
    }
}
