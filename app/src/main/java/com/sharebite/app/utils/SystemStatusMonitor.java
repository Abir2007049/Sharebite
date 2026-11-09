package com.sharebite.app.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Listens to system broadcasts that change how ShareBite should behave:
 *
 * <ul>
 *   <li>{@link Intent#ACTION_AIRPLANE_MODE_CHANGED}: the app is offline. Firestore keeps serving
 *       cached listings and queues writes, so we say so instead of showing errors.</li>
 *   <li>{@link Intent#ACTION_BATTERY_LOW} / {@link Intent#ACTION_BATTERY_OKAY} and
 *       {@link PowerManager#ACTION_POWER_SAVE_MODE_CHANGED}: low-power mode. Decorative animations
 *       (loading pulses, count-ups) are switched off to save battery.</li>
 * </ul>
 *
 * Registered at runtime (not in the manifest) while an activity is visible: since Android 8 most
 * implicit broadcasts can't be received by manifest receivers, and we only need them on screen.
 */
public final class SystemStatusMonitor extends BroadcastReceiver {

    /** Current device state as far as the app cares. */
    public static final class Status {
        public final boolean airplaneMode;
        public final boolean batteryLow;
        public final boolean powerSaver;

        Status(boolean airplaneMode, boolean batteryLow, boolean powerSaver) {
            this.airplaneMode = airplaneMode;
            this.batteryLow = batteryLow;
            this.powerSaver = powerSaver;
        }

        public boolean lowPower() {
            return batteryLow || powerSaver;
        }
    }

    private static final MutableLiveData<Status> STATUS = new MutableLiveData<>(new Status(false, false, false));
    private static volatile boolean lowPower;

    /** App-wide: observed by MainActivity's banner. */
    public static LiveData<Status> status() {
        return STATUS;
    }

    /** Cheap check for animation code (SkeletonLayout, Motion). */
    public static boolean isLowPower() {
        return lowPower;
    }

    private boolean batteryLow;

    /** Registers for the broadcasts and publishes the current state immediately. */
    public void register(@NonNull Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        // System broadcasts still arrive with NOT_EXPORTED; other apps can't fake them to us.
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        batteryLow = readBatteryLow(context);
        publish(context);
    }

    public void unregister(@NonNull Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException ignored) {
            // Not registered.
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BATTERY_LOW.equals(action)) batteryLow = true;
        else if (Intent.ACTION_BATTERY_OKAY.equals(action)) batteryLow = false;
        publish(context);
    }

    private void publish(Context context) {
        boolean airplane = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean saver = pm != null && pm.isPowerSaveMode();
        Status s = new Status(airplane, batteryLow, saver);
        lowPower = s.lowPower();
        STATUS.setValue(s);
    }

    /** The battery level from the sticky ACTION_BATTERY_CHANGED broadcast (no receiver needed). */
    private static boolean readBatteryLow(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return false;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return level >= 0 && scale > 0 && plugged == 0 && level * 100f / scale <= 15f;
    }
}
