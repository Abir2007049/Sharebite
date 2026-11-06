package com.sharebite.app.domain.interfaces;

import com.sharebite.app.data.model.AppNotification;

import java.util.List;

/** In-app notifications in notifications/{id}; each user only sees their own. */
public interface NotificationRepository {

    Subscription observeForRecipient(String recipientId, ResultCallback<List<AppNotification>> callback);

    void markRead(String notificationId, ResultCallback<Void> callback);

    void markAllRead(List<AppNotification> notifications, ResultCallback<Void> callback);
}
