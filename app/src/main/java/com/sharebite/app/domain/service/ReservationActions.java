package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;

/**
 * Which actions a viewer can take on a reservation. The UI shows exactly these buttons; the
 * repository transaction and security rules enforce the same transitions independently.
 */
public final class ReservationActions {

    public enum Action { CONFIRM, REJECT, CANCEL, COLLECT }

    /** Primary (filled) and secondary (outlined) actions; either may be null. */
    public static final class Available {
        @Nullable public final Action primary;
        @Nullable public final Action secondary;

        Available(@Nullable Action primary, @Nullable Action secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        public boolean isEmpty() {
            return primary == null && secondary == null;
        }
    }

    private ReservationActions() {
    }

    @NonNull
    public static Available forViewer(@NonNull Reservation r, @Nullable String viewerUid) {
        ReservationStatus s = r.getStatus();
        if (viewerUid == null || s == null || s.isTerminal()) return new Available(null, null);
        boolean provider = viewerUid.equals(r.getProviderId());
        boolean receiver = viewerUid.equals(r.getReceiverId());
        if (provider) {
            return s == ReservationStatus.PENDING
                    ? new Available(Action.CONFIRM, Action.REJECT)
                    : new Available(Action.COLLECT, Action.CANCEL);
        }
        if (receiver) {
            return s == ReservationStatus.PENDING
                    ? new Available(null, Action.CANCEL)
                    : new Available(Action.COLLECT, Action.CANCEL);
        }
        return new Available(null, null);
    }
}
