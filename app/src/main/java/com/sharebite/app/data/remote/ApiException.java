package com.sharebite.app.data.remote;

import androidx.annotation.NonNull;

import java.io.IOException;

/** Failure of an external HTTP API call, classified so the UI can show a precise message. */
public class ApiException extends IOException {

    public enum Kind {
        /** No connectivity / DNS / connection refused. */
        NETWORK,
        TIMEOUT,
        /** Non-2xx HTTP status. */
        HTTP,
        /** Response body was not the JSON we expected. */
        INVALID_RESPONSE,
        /** Valid response, but nothing matched. */
        NO_RESULTS
    }

    private final Kind kind;
    private final int httpStatus;

    public ApiException(@NonNull Kind kind, @NonNull String message) {
        this(kind, message, -1, null);
    }

    public ApiException(@NonNull Kind kind, @NonNull String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    @NonNull
    public Kind getKind() {
        return kind;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
