package com.sharebite.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Generic screen state: loading, data, or an error message.
 *
 * @param <T> type of the loaded data
 */
public final class UiState<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    @NonNull public final Status status;
    @Nullable public final T data;
    @Nullable public final String message;

    private UiState(@NonNull Status status, @Nullable T data, @Nullable String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    public static <T> UiState<T> loading(@Nullable T previous) {
        return new UiState<>(Status.LOADING, previous, null);
    }

    public static <T> UiState<T> success(@Nullable T data) {
        return new UiState<>(Status.SUCCESS, data, null);
    }

    public static <T> UiState<T> error(@NonNull String message, @Nullable T previous) {
        return new UiState<>(Status.ERROR, previous, message);
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }
}
