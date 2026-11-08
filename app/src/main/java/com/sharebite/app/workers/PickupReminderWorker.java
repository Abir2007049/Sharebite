package com.sharebite.app.workers;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sharebite.app.FirebaseSetup;
import com.sharebite.app.R;
import com.sharebite.app.data.repository.FirestoreReservationRepository;
import com.sharebite.app.ui.reservation.ReservationDetailActivity;

import java.util.concurrent.TimeUnit;

/**
 * Shows the "pickup closes soon" notification. Before notifying it re-reads the reservation, so a
 * reservation cancelled or collected while the app was closed does not produce a stale reminder.
 * If that check fails (for example offline) it still reminds: a spare reminder beats a missed pickup.
 */
public class PickupReminderWorker extends Worker {

    public static final String CHANNEL_ID = "pickup_reminders";

    public PickupReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        String id = getInputData().getString(PickupReminders.KEY_RESERVATION_ID);
        if (id == null || !stillConfirmed(c, id)) return Result.success();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }
        ensureChannel(c);
        String title = valueOr(getInputData().getString(PickupReminders.KEY_TITLE));
        String address = valueOr(getInputData().getString(PickupReminders.KEY_ADDRESS));

        Intent open = ReservationDetailActivity.intent(c, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(c, id.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder n = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_schedule)
                .setColor(ContextCompat.getColor(c, R.color.forest_green))
                .setContentTitle(c.getString(R.string.reminder_title, PickupReminders.LEAD_MINUTES))
                .setContentText(c.getString(R.string.reminder_text, title))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(c.getString(R.string.reminder_text_long, title, address)))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tap)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(c).notify(PickupReminders.workName(id).hashCode(), n.build());
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the call.
        }
        return Result.success();
    }

    private static String valueOr(String s) {
        return s == null ? "" : s;
    }

    private static boolean stillConfirmed(Context c, String reservationId) {
        if (!FirebaseSetup.isReady(c)) return true;
        try {
            DocumentSnapshot snap = Tasks.await(FirebaseFirestore.getInstance()
                    .collection(FirestoreReservationRepository.COLLECTION).document(reservationId).get(), 15, TimeUnit.SECONDS);
            return !snap.exists() || "CONFIRMED".equals(snap.getString("status"));
        } catch (Exception e) {
            return true;
        }
    }

    public static void ensureChannel(@NonNull Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, c.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.reminder_channel_description));
        nm.createNotificationChannel(ch);
    }
}
