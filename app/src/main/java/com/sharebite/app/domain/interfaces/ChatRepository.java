package com.sharebite.app.domain.interfaces;

import com.sharebite.app.data.model.ChatMessage;
import com.sharebite.app.data.model.User;

import java.util.List;

/** Messages between the two parties of a reservation. */
public interface ChatRepository {

    /** Realtime, oldest first. Messages still being sent carry an estimated timestamp. */
    Subscription observe(String reservationId, ResultCallback<List<ChatMessage>> callback);

    void send(String reservationId, User sender, String text, ResultCallback<Void> callback);
}
