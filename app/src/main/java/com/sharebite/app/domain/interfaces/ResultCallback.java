package com.sharebite.app.domain.interfaces;

import androidx.annotation.NonNull;

/**
 * Generic asynchronous result. Repositories deliver callbacks on the main thread, so
 * implementations can update LiveData or views directly.
 *
 * @param <T> type of the successful result
 */
public interface ResultCallback<T> {

    void onSuccess(T result);

    void onError(@NonNull Exception error);
}
