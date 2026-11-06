package com.sharebite.app.utils;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.storage.StorageException;
import com.sharebite.app.data.model.BusinessRuleException;
import com.sharebite.app.data.remote.ApiException;

/** Converts exceptions into short, actionable messages for users. */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    @NonNull
    public static String from(@NonNull Throwable error) {
        // Business-rule violations may arrive wrapped (e.g. thrown inside a transaction).
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof BusinessRuleException || t instanceof ApiException) {
                return t.getMessage() == null ? "Something went wrong." : t.getMessage();
            }
            if (t.getCause() == t) break;
        }

        if (error instanceof FirebaseNetworkException) {
            return "You appear to be offline. Check your connection and try again.";
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a few minutes and try again.";
        }
        if (error instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email already exists. Try logging in instead.";
        }
        if (error instanceof FirebaseAuthWeakPasswordException) {
            return "That password is too weak. Use at least 8 characters with letters and numbers.";
        }
        if (error instanceof FirebaseAuthInvalidUserException) {
            return "No account matches these details, or it has been disabled.";
        }
        if (error instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        if (error instanceof FirebaseAuthException) {
            return "Authentication failed. Please try again.";
        }
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case PERMISSION_DENIED:
                    return "You don't have permission to do that.";
                case UNAVAILABLE:
                    return "The service is temporarily unavailable. Please try again.";
                case NOT_FOUND:
                    return "This item no longer exists.";
                case ABORTED:
                    return "Someone else updated this at the same time. Please try again.";
                case FAILED_PRECONDITION:
                    return "The database isn't fully set up (missing index or rules). See README.";
                default:
                    return "Something went wrong while saving. Please try again.";
            }
        }
        if (error instanceof StorageException) {
            int code = ((StorageException) error).getErrorCode();
            if (code == StorageException.ERROR_NOT_AUTHORIZED) return "Image upload was not permitted.";
            if (code == StorageException.ERROR_QUOTA_EXCEEDED) return "Image storage quota exceeded.";
            if (code == StorageException.ERROR_RETRY_LIMIT_EXCEEDED) return "Image upload timed out. Check your connection.";
            if (code == StorageException.ERROR_BUCKET_NOT_FOUND || code == StorageException.ERROR_PROJECT_NOT_FOUND) {
                return "Firebase Storage is not enabled for this project. See README.";
            }
            return "Image upload failed. Please try again.";
        }
        String message = error.getMessage();
        return message == null || message.isEmpty() ? "Something went wrong. Please try again." : message;
    }
}
