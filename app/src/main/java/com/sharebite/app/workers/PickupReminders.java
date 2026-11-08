package com.sharebite.app.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Keeps one local reminder per confirmed reservation, scheduled {@link #LEAD_MINUTES} minutes
 * before its pickup deadline. Called whenever the receiver's reservation list changes; reminders
 * for reservations that are no longer confirmed are cancelled. No server is involved.
 */
public final class PickupReminders {

    public static final int LEAD_MINUTES = 30;
    static final String KEY_RESERVATION_ID = "reservationId";
    static final String KEY_TITLE = "title";
    static final String KEY_ADDRESS = "address";
    private static final String TAG = "pickup-reminder";

    private PickupReminders() { }

    /** Milliseconds from {@code nowMs} until the reminder should fire, or -1 if it is too late. */
    public static long delayUntilReminder(long deadlineMs, long nowMs) {
        long fireAt = deadlineMs - TimeUnit.MINUTES.toMillis(LEAD_MINUTES);
        return fireAt > nowMs ? fireAt - nowMs : -1;
    }

    public static void sync(@NonNull Context context, @NonNull List<Reservation> reservations, @NonNull String receiverId) {
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        long now = System.currentTimeMillis();
        for (Reservation r : reservations) {
            if (r.getReservationId() == null || !receiverId.equals(r.getReceiverId())) continue;
            String name = workName(r.getReservationId());
            long delay = r.getStatus() == ReservationStatus.CONFIRMED && r.getPickupDeadline() != null
                    ? delayUntilReminder(r.getPickupDeadline().toDate().getTime(), now) : -1;
            if (delay < 0) {
                wm.cancelUniqueWork(name);
                continue;
            }
            Data input = new Data.Builder()
                    .putString(KEY_RESERVATION_ID, r.getReservationId())
                    .putString(KEY_TITLE, r.getListingTitle())
                    .putString(KEY_ADDRESS, r.getPickupAddress())
                    .build();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PickupReminderWorker.class)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(input)
                    .addTag(TAG)
                    .build();
            // KEEP: a reservation's deadline never changes, so an already-scheduled reminder is still right.
            wm.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request);
        }
    }

    /** Cancels every pending reminder (on sign-out). */
    public static void cancelAll(@NonNull Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelAllWorkByTag(TAG);
    }

    static String workName(String reservationId) {
        return "pickup-reminder-" + reservationId;
    }
}
