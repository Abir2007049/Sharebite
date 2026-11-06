package com.sharebite.app.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.sharebite.app.domain.interfaces.ResultCallback;

/**
 * Obtains a real device location (never a guessed or default one). Callers must request the
 * location permission first; without it, or without a fix, the app simply hides distances.
 */
public final class LocationHelper {

    private final FusedLocationProviderClient client;
    private CancellationTokenSource tokenSource;

    public LocationHelper(@NonNull Context context) {
        client = LocationServices.getFusedLocationProviderClient(context.getApplicationContext());
    }

    public static boolean hasPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    };

    /** Delivers a recent location; onError when unavailable (location off, no fix). */
    @SuppressLint("MissingPermission")
    public void currentLocation(@NonNull Context context, @NonNull ResultCallback<Location> callback) {
        if (!hasPermission(context)) {
            callback.onError(new SecurityException("Location permission not granted"));
            return;
        }
        cancel();
        tokenSource = new CancellationTokenSource();
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(5 * 60 * 1000)
                .setDurationMillis(15_000)
                .build();
        client.getCurrentLocation(request, tokenSource.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) callback.onSuccess(location);
                    else callback.onError(new IllegalStateException("Location is unavailable. Is location turned on?"));
                })
                .addOnFailureListener(callback::onError);
    }

    public void cancel() {
        if (tokenSource != null) {
            tokenSource.cancel();
            tokenSource = null;
        }
    }
}
