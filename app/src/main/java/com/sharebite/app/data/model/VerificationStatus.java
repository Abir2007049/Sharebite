package com.sharebite.app.data.model;

import androidx.annotation.StringRes;

import com.sharebite.app.R;

/**
 * Charity verification lifecycle. Only a reviewer (see README: Firebase console / admin claim)
 * can move an organisation to VERIFIED or REJECTED; security rules block self-verification.
 */
public enum VerificationStatus {
    /** Providers and receivers do not need verification. */
    NOT_REQUIRED(R.string.verification_not_required),
    /** Charity registered but has not submitted documents yet. */
    UNVERIFIED(R.string.verification_unverified),
    PENDING(R.string.verification_pending),
    VERIFIED(R.string.verification_verified),
    REJECTED(R.string.verification_rejected);

    @StringRes public final int labelRes;

    VerificationStatus(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    /** A charity may (re)submit its details when it has never submitted or was rejected. */
    public boolean canSubmit() {
        return this == UNVERIFIED || this == REJECTED;
    }
}
