package com.sharebite.app.workers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.sharebite.app.R;
import com.sharebite.app.data.repository.FirestoreFoodRepository;
import com.sharebite.app.data.repository.FirestoreReservationRepository;
import com.sharebite.app.domain.service.DistanceCalculator;
import com.sharebite.app.ui.reservation.ReservationDetailActivity;
import com.sharebite.app.utils.Formatters;

/**
 * "I'm on my way" foreground service for a receiver heading to a pickup. It keeps an ongoing
 * notification up to date with the distance to the pickup point (from real location fixes) and the
 * time left before the pickup window closes. It stops by itself when the window closes or when the
 * reservation is no longer confirmed (collected, cancelled...), or from the notification's Stop
 * action. Type "location": it is started while the app is on screen, as Android requires.
 */
public class OnMyWayService extends Service {

    public static final String CHANNEL_ID = "on_my_way";
    private static final int NOTIFICATION_ID = 4711;
    private static final String EXTRA_RESERVATION = "reservationId";
    private static final String EXTRA_LISTING = "listingId";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_PLACE = "place";
    private static final String EXTRA_DEADLINE = "deadline";
    private static final String ACTION_STOP = "com.sharebite.app.action.STOP_ON_MY_WAY";
    private static final long TICK_MS = 30_000;

    public static Intent start(Context c, String reservationId, String listingId, String title, String place, long deadlineMs) {
        return new Intent(c, OnMyWayService.class).putExtra(EXTRA_RESERVATION, reservationId)
                .putExtra(EXTRA_LISTING, listingId).putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PLACE, place).putExtra(EXTRA_DEADLINE, deadlineMs);
    }

    public static Intent stop(Context c) {
        return new Intent(c, OnMyWayService.class).setAction(ACTION_STOP);
    }

    /** True while the service runs (for the button label). */
    public static volatile String activeReservationId;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FusedLocationProviderClient location;
    private ListenerRegistration reservationListener;
    private String reservationId, title, place;
    private long deadline, startedAt;
    private double[] target;   // pickup coordinates, if the listing has them
    private Location lastFix;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() >= deadline) {
                stopSelf();
                return;
            }
            update();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            lastFix = result.getLastLocation();
            update();
        }
    };

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        reservationId = intent.getStringExtra(EXTRA_RESERVATION);
        title = intent.getStringExtra(EXTRA_TITLE);
        place = intent.getStringExtra(EXTRA_PLACE);
        deadline = intent.getLongExtra(EXTRA_DEADLINE, 0);
        startedAt = System.currentTimeMillis();
        if (reservationId == null || !hasLocationPermission()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureChannel(this);
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(),
                Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
        activeReservationId = reservationId;

        loadTarget(intent.getStringExtra(EXTRA_LISTING));
        watchReservation();
        startLocationUpdates();
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_NOT_STICKY;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission") // checked in onStartCommand
    private void startLocationUpdates() {
        location = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000)
                .setMinUpdateIntervalMillis(10_000).build();
        location.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    private void loadTarget(@Nullable String listingId) {
        if (listingId == null) return;
        FirebaseFirestore.getInstance().collection(FirestoreFoodRepository.COLLECTION).document(listingId).get()
                .addOnSuccessListener(snap -> {
                    Double lat = snap.getDouble("latitude"), lng = snap.getDouble("longitude");
                    if (lat != null && lng != null) {
                        target = new double[]{lat, lng};
                        update();
                    }
                });
    }

    /** Stop automatically once the reservation is collected, cancelled or declined. */
    private void watchReservation() {
        if (reservationListener != null) reservationListener.remove();
        reservationListener = FirebaseFirestore.getInstance().collection(FirestoreReservationRepository.COLLECTION)
                .document(reservationId).addSnapshotListener((snap, error) -> {
                    if (snap != null && snap.exists() && !"CONFIRMED".equals(snap.getString("status"))) stopSelf();
                });
    }

    private void update() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, build());
    }

    private Notification build() {
        long left = Math.max(0, deadline - System.currentTimeMillis());
        String timeLeft = getString(R.string.omw_time_left, Formatters.minutesLeft(left));
        String text;
        if (target != null && lastFix != null) {
            double km = DistanceCalculator.distanceKm(lastFix.getLatitude(), lastFix.getLongitude(), target[0], target[1]);
            text = getString(R.string.omw_distance, Formatters.distance(this, km), timeLeft);
        } else {
            text = target == null ? timeLeft : getString(R.string.omw_locating, timeLeft);
        }
        Intent open = ReservationDetailActivity.intent(this, reservationId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, stop(this), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Progress bar: share of the time between "on my way" and the deadline that has passed.
        long total = Math.max(1, deadline - startedAt);
        int progress = (int) Math.min(100, Math.max(0, (System.currentTimeMillis() - startedAt) * 100 / total));
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_directions)
                .setColor(ContextCompat.getColor(this, R.color.forest_green))
                .setContentTitle(getString(R.string.omw_title, place == null ? (title == null ? "" : title) : place))
                .setContentText(text)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setContentIntent(tap)
                .addAction(0, getString(R.string.omw_stop), stop)
                .build();
    }

    public static void ensureChannel(@NonNull Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, c.getString(R.string.omw_channel), NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(c.getString(R.string.omw_channel_description));
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (location != null) location.removeLocationUpdates(locationCallback);
        if (reservationListener != null) reservationListener.remove();
        activeReservationId = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
