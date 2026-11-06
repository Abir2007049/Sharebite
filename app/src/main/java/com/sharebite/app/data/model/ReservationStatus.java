package com.sharebite.app.data.model;

import androidx.annotation.StringRes;

import com.sharebite.app.R;

/**
 * Reservation state machine. The same transitions are enforced server-side in firestore.rules.
 *
 * <pre>
 * PENDING ──confirm──▶ CONFIRMED ──collect──▶ COLLECTED
 *    │                     │
 *    ├──reject──▶ REJECTED │
 *    └──cancel──▶ CANCELLED ◀──cancel──┘
 * </pre>
 */
public enum ReservationStatus {
    PENDING(R.string.reservation_status_pending),
    CONFIRMED(R.string.reservation_status_confirmed),
    REJECTED(R.string.reservation_status_rejected),
    CANCELLED(R.string.reservation_status_cancelled),
    COLLECTED(R.string.reservation_status_collected);

    @StringRes public final int labelRes;

    ReservationStatus(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    public boolean canTransitionTo(ReservationStatus target) {
        if (target == null) return false;
        switch (this) {
            case PENDING:
                return target == CONFIRMED || target == REJECTED || target == CANCELLED;
            case CONFIRMED:
                return target == COLLECTED || target == CANCELLED;
            default:
                // REJECTED, CANCELLED and COLLECTED are terminal.
                return false;
        }
    }

    /** Active reservations still hold quantity on the listing. */
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean isTerminal() {
        return !isActive();
    }
}
