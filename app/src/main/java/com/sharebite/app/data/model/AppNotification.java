package com.sharebite.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * In-app notification stored in notifications/{id}. Named AppNotification to avoid clashing
 * with {@code android.app.Notification}.
 */
public class AppNotification implements FirestoreDocument {

    private String notificationId;
    private String recipientId;
    private String senderId;
    private String title;
    private String message;
    private NotificationType type;
    private String relatedListingId;
    private String relatedReservationId;
    private boolean read;
    @ServerTimestamp private Timestamp createdAt;

    public AppNotification() {
        // Required by Firestore.
    }

    public AppNotification(String notificationId, String recipientId, String senderId, String title,
                           String message, NotificationType type, String relatedListingId,
                           String relatedReservationId, Timestamp createdAt) {
        this.notificationId = notificationId;
        this.recipientId = recipientId;
        this.senderId = senderId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.relatedListingId = relatedListingId;
        this.relatedReservationId = relatedReservationId;
        this.read = false;
        this.createdAt = createdAt;
    }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getRelatedListingId() { return relatedListingId; }
    public void setRelatedListingId(String relatedListingId) { this.relatedListingId = relatedListingId; }

    public String getRelatedReservationId() { return relatedReservationId; }
    public void setRelatedReservationId(String relatedReservationId) { this.relatedReservationId = relatedReservationId; }

    /** Stored as "isRead" to match the documented schema. */
    @PropertyName("isRead")
    public boolean isRead() { return read; }

    @PropertyName("isRead")
    public void setRead(boolean read) { this.read = read; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String documentId() { return notificationId; }

    @Override
    public void assignDocumentId(String id) { this.notificationId = id; }
}
