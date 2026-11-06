package com.sharebite.app.data.repository;

import androidx.annotation.NonNull;

import com.sharebite.app.data.model.BusinessRuleException;

/**
 * Unchecked carrier used to abort a Firestore transaction with a business-rule violation.
 * Firestore only retries transactions on its own retryable error codes, so throwing this stops
 * the transaction immediately; {@code ErrorMessages} unwraps it for the UI.
 */
final class TransactionAbort extends RuntimeException {

    TransactionAbort(@NonNull BusinessRuleException cause) {
        super(cause.getMessage(), cause);
    }

    static TransactionAbort of(BusinessRuleException.Reason reason, String message) {
        return new TransactionAbort(new BusinessRuleException(reason, message));
    }
}
