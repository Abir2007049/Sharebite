package com.sharebite.app.domain.interfaces;

/**
 * Generic read contract shared by all document repositories.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public interface Repository<T, ID> {

    /** One-shot read. Delivers {@code null} via onSuccess if the entity does not exist. */
    void getById(ID id, ResultCallback<T> callback);

    /** Realtime read: delivers the current value, then every change, until cancelled. */
    Subscription observeById(ID id, ResultCallback<T> callback);
}
