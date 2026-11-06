package com.sharebite.app.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.sharebite.app.data.model.FirestoreDocument;
import com.sharebite.app.domain.interfaces.Repository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic Firestore repository: maps documents of one collection onto {@code T} and provides
 * one-shot and realtime reads. Subclasses add collection-specific writes and queries.
 *
 * @param <T> model type stored in the collection
 */
public abstract class BaseFirestoreRepository<T extends FirestoreDocument> implements Repository<T, String> {

    private static final String TAG = "FirestoreRepository";

    protected final FirebaseFirestore db;
    protected final CollectionReference collection;
    private final Class<? extends T> type;

    protected BaseFirestoreRepository(@NonNull FirebaseFirestore db, @NonNull String collectionPath,
                                      @NonNull Class<? extends T> type) {
        this.db = db;
        this.collection = db.collection(collectionPath);
        this.type = type;
    }

    /** Converts a snapshot into a model. Override for polymorphic collections (users). */
    @Nullable
    protected T map(@NonNull DocumentSnapshot snapshot) {
        if (!snapshot.exists()) return null;
        T value = snapshot.toObject(type);
        if (value != null) value.assignDocumentId(snapshot.getId());
        return value;
    }

    @Override
    public void getById(String id, ResultCallback<T> callback) {
        collection.document(id).get()
                .addOnSuccessListener(snapshot -> {
                    try {
                        callback.onSuccess(map(snapshot));
                    } catch (RuntimeException e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public Subscription observeById(String id, ResultCallback<T> callback) {
        ListenerRegistration registration = collection.document(id).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
            } else if (snapshot != null) {
                try {
                    callback.onSuccess(map(snapshot));
                } catch (RuntimeException e) {
                    callback.onError(e);
                }
            }
        });
        return registration::remove;
    }

    /** Realtime query; documents that fail to map are skipped (and logged) rather than failing the list. */
    protected Subscription observeQuery(@NonNull Query query, @NonNull ResultCallback<List<T>> callback) {
        ListenerRegistration registration = query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshots == null) return;
            callback.onSuccess(mapAll(snapshots.getDocuments()));
        });
        return registration::remove;
    }

    protected void getQuery(@NonNull Query query, @NonNull ResultCallback<List<T>> callback) {
        query.get()
                .addOnSuccessListener(snapshots -> callback.onSuccess(mapAll(snapshots.getDocuments())))
                .addOnFailureListener(callback::onError);
    }

    @NonNull
    protected List<T> mapAll(@NonNull List<DocumentSnapshot> documents) {
        List<T> out = new ArrayList<>(documents.size());
        for (DocumentSnapshot doc : documents) {
            try {
                T value = map(doc);
                if (value != null) out.add(value);
            } catch (RuntimeException e) {
                Log.w(TAG, "Skipping malformed document " + doc.getReference().getPath(), e);
            }
        }
        return out;
    }

    /** Adapts a Task to a callback, discarding its result. */
    protected static void deliverVoid(@NonNull Task<?> task, @NonNull ResultCallback<Void> callback) {
        task.addOnSuccessListener(ignored -> callback.onSuccess(null)).addOnFailureListener(callback::onError);
    }
}
