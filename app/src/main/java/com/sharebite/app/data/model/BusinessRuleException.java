package com.sharebite.app.data.model;

import androidx.annotation.NonNull;

/** Thrown when an operation would violate a ShareBite business rule. */
public class BusinessRuleException extends Exception {

    public enum Reason {
        LISTING_NOT_AVAILABLE,
        LISTING_EXPIRED,
        PICKUP_NOT_STARTED,
        INSUFFICIENT_QUANTITY,
        INVALID_QUANTITY,
        NOT_PERMITTED,
        INVALID_STATE,
        VERIFICATION_REQUIRED,
        NOT_FOUND
    }

    private final Reason reason;

    public BusinessRuleException(@NonNull Reason reason, @NonNull String message) {
        super(message);
        this.reason = reason;
    }

    @NonNull
    public Reason getReason() {
        return reason;
    }
}
