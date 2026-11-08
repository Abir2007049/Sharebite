package com.sharebite.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

/**
 * A message between the provider and the receiver of one reservation, stored at
 * reservations/{reservationId}/messages/{messageId}. Only those two people can read or write them.
 */
public class ChatMessage implements FirestoreDocument {

    public static final int MAX_LENGTH = 500;

    private String messageId;
    private String senderId;
    private String senderName;
    private String text;
    @ServerTimestamp private Timestamp createdAt;

    public ChatMessage() {
        // Required by Firestore.
    }

    public ChatMessage(String messageId, String senderId, String senderName, String text) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
    }

    public boolean isFrom(String uid) {
        return uid != null && uid.equals(senderId);
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String documentId() { return messageId; }

    @Override
    public void assignDocumentId(String id) { this.messageId = id; }
}
