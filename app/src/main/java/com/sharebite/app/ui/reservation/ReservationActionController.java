package com.sharebite.app.ui.reservation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharebite.app.R;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.ReservationRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.service.ReservationActions.Action;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.ServiceLocator;

/**
 * Runs a reservation action after asking for confirmation. Guards against double taps: while
 * one action is in flight, further requests are ignored (the transaction also rejects a second
 * transition, so this is purely to avoid confusing error messages).
 */
public final class ReservationActionController {

    public interface Listener {
        void onBusy(boolean busy);

        void onResult(@NonNull String message, boolean success);
    }

    private final ReservationRepository repository = ServiceLocator.get().reservations();
    private boolean busy;

    public void request(@NonNull Context context, @NonNull Reservation r, @NonNull Action action,
                        @NonNull User actor, @NonNull Listener listener) {
        if (busy) return;
        boolean isProvider = actor.getUid().equals(r.getProviderId());
        @StringRes int title, message, confirm;
        switch (action) {
            case CONFIRM:
                title = R.string.dialog_confirm_title; message = R.string.dialog_confirm_message; confirm = R.string.action_confirm;
                break;
            case REJECT:
                title = R.string.dialog_decline_title; message = R.string.dialog_decline_message; confirm = R.string.action_decline;
                break;
            case COLLECT:
                title = R.string.dialog_collect_title;
                message = isProvider ? R.string.dialog_collect_message_provider : R.string.dialog_collect_message_receiver;
                confirm = isProvider ? R.string.action_mark_collected : R.string.action_confirm_pickup;
                break;
            case CANCEL:
            default:
                title = R.string.dialog_cancel_title;
                message = isProvider ? R.string.dialog_cancel_message_provider : R.string.dialog_cancel_message_receiver;
                confirm = R.string.action_cancel_reservation;
                break;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(confirm, (d, w) -> execute(context, r, action, actor, listener))
                .show();
    }

    private void execute(Context context, Reservation r, Action action, User actor, Listener listener) {
        busy = true;
        listener.onBusy(true);
        ResultCallback<Void> callback = new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                busy = false;
                listener.onBusy(false);
                listener.onResult(context.getString(successMessage(action)), true);
            }

            @Override
            public void onError(@NonNull Exception error) {
                busy = false;
                listener.onBusy(false);
                listener.onResult(ErrorMessages.from(error), false);
            }
        };
        switch (action) {
            case CONFIRM: repository.confirm(r, actor, callback); break;
            case REJECT: repository.reject(r, actor, callback); break;
            case COLLECT: repository.markCollected(r, actor, callback); break;
            case CANCEL: default: repository.cancel(r, actor, callback); break;
        }
    }

    @StringRes
    private static int successMessage(Action action) {
        switch (action) {
            case CONFIRM: return R.string.result_confirmed;
            case REJECT: return R.string.result_declined;
            case COLLECT: return R.string.result_collected;
            case CANCEL: default: return R.string.result_cancelled;
        }
    }
}
