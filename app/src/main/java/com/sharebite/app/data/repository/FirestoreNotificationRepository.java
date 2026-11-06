package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.sharebite.app.data.model.AppNotification;
import com.sharebite.app.domain.interfaces.NotificationRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** notifications/{id}: read and mark-as-read for the recipient. */
public class FirestoreNotificationRepository extends BaseFirestoreRepository<AppNotification> implements NotificationRepository {

    /** Only the most recent notifications are shown. */
    private static final int MAX_SHOWN = 100;

    public FirestoreNotificationRepository(@NonNull FirebaseFirestore db) {
        super(db, NotificationWriter.COLLECTION, AppNotification.class);
    }

    @Override
    public Subscription observeForRecipient(String recipientId, ResultCallback<List<AppNotification>> callback) {
        // Single-field equality query (no composite index needed); sorted client-side.
        return observeQuery(collection.whereEqualTo("recipientId", recipientId), new ResultCallback<List<AppNotification>>() {
            @Override
            public void onSuccess(List<AppNotification> result) {
                List<AppNotification> sorted = new ArrayList<>(result);
                Collections.sort(sorted, (a, b) -> Long.compare(millis(b.getCreatedAt()), millis(a.getCreatedAt())));
                callback.onSuccess(sorted.size() > MAX_SHOWN ? sorted.subList(0, MAX_SHOWN) : sorted);
            }

            @Override
            public void onError(@NonNull Exception error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void markRead(String notificationId, ResultCallback<Void> callback) {
        deliverVoid(collection.document(notificationId).update("isRead", true), callback);
    }

    @Override
    public void markAllRead(List<AppNotification> list, ResultCallback<Void> callback) {
        WriteBatch batch = db.batch();
        int count = 0;
        for (AppNotification n : list) {
            if (!n.isRead() && count < 450) { // Batches are limited to 500 writes.
                batch.update(collection.document(n.getNotificationId()), "isRead", true);
                count++;
            }
        }
        if (count == 0) {
            callback.onSuccess(null);
            return;
        }
        deliverVoid(batch.commit(), callback);
    }

    private static long millis(Timestamp t) {
        return t == null ? Long.MAX_VALUE : t.toDate().getTime();
    }
}
