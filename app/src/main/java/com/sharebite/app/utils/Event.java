package com.sharebite.app.utils;

import androidx.annotation.Nullable;

/**
 * Wraps a one-off value exposed through LiveData (a toast, a navigation command) so it is
 * handled once, not again after a configuration change re-delivers the last value.
 */
public final class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** Returns the content the first time; null afterwards. */
    @Nullable
    public T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() {
        return content;
    }
}
