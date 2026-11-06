package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;
import com.sharebite.app.data.model.AppNotification;
import com.sharebite.app.data.model.NotificationType;
import com.sharebite.app.data.model.Reservation;

import java.util.Locale;

/**
 * Builds in-app notifications for reservation events and writes them in the same transaction or
 * batch as the change they describe, so a notification exists if and only if the change happened.
 * Security rules only accept a notification whose related reservation involves both parties.
 */
final class NotificationWriter {

    static final String COLLECTION = "notifications";

    private final FirebaseFirestore db;

    NotificationWriter(@NonNull FirebaseFirestore db) {
        this.db = db;
    }

    void write(@NonNull Transaction tx, @NonNull AppNotification n) {
        tx.set(db.collection(COLLECTION).document(n.getNotificationId()), n);
    }

    void write(@NonNull WriteBatch batch, @NonNull AppNotification n) {
        batch.set(db.collection(COLLECTION).document(n.getNotificationId()), n);
    }

    @NonNull
    AppNotification forEvent(@NonNull NotificationType type, @NonNull Reservation r, @NonNull String senderId) {
        boolean toProvider = type == NotificationType.RESERVATION_REQUEST
                || (type == NotificationType.RESERVATION_CANCELLED && senderId.equals(r.getReceiverId()))
                || (type == NotificationType.COLLECTION_COMPLETED && senderId.equals(r.getReceiverId()));
        String recipient = toProvider ? r.getProviderId() : r.getReceiverId();
        String qty = r.getQuantity() + " × " + r.getListingTitle();
        String title;
        String message;
        switch (type) {
            case RESERVATION_REQUEST:
                title = "New reservation request";
                message = r.getReceiverName() + " would like to collect " + qty + ".";
                break;
            case RESERVATION_CONFIRMED:
                title = "Reservation confirmed";
                message = r.getProviderName() + " confirmed your reservation for " + qty + ". Collect it before the pickup deadline.";
                break;
            case RESERVATION_REJECTED:
                title = "Reservation declined";
                message = r.getProviderName() + " could not accept your reservation for " + qty + ".";
                break;
            case RESERVATION_CANCELLED:
                title = "Reservation cancelled";
                message = toProvider
                        ? r.getReceiverName() + " cancelled their reservation for " + qty + ". The quantity is available again."
                        : r.getProviderName() + " cancelled your reservation for " + qty + ".";
                break;
            case COLLECTION_COMPLETED:
                title = "Collection completed";
                message = String.format(Locale.getDefault(), "%s was marked as collected. Thank you for rescuing food!", qty);
                break;
            case LISTING_CANCELLED:
            default:
                title = "Listing withdrawn";
                message = r.getProviderName() + " withdrew \"" + r.getListingTitle() + "\". Your reservation has been closed.";
                break;
        }
        String id = db.collection(COLLECTION).document().getId();
        // createdAt null → filled with the server time by @ServerTimestamp.
        return new AppNotification(id, recipient, senderId, title, message, type, r.getListingId(), r.getReservationId(), null);
    }
}
