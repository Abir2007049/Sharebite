package com.sharebite.app.ui;

import android.location.Location;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.utils.LocationHelper;

import java.util.Map;

/**
 * Fragment-scoped "ask for permission, then get a location" flow. Must be created during the
 * fragment's construction or onCreate (ActivityResult registration rule). Pending location
 * requests are cancelled when the fragment is destroyed.
 */
public final class LocationRequester implements DefaultLifecycleObserver {

    public interface Callback {
        void onLocation(@NonNull double[] latLng);

        void onUnavailable(@NonNull String reason);
    }

    private final Fragment fragment;
    private final Callback callback;
    private final ActivityResultLauncher<String[]> launcher;
    private LocationHelper helper;

    public LocationRequester(@NonNull Fragment fragment, @NonNull Callback callback) {
        this.fragment = fragment;
        this.callback = callback;
        this.launcher = fragment.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);
        fragment.getLifecycle().addObserver(this);
    }

    public boolean hasPermission() {
        return LocationHelper.hasPermission(fragment.requireContext());
    }

    /** Requests permission if needed, then fetches the current location. */
    public void request() {
        if (hasPermission()) fetch();
        else launcher.launch(LocationHelper.PERMISSIONS);
    }

    /** Fetches only if permission was already granted (no prompt). */
    public void fetchIfPermitted() {
        if (hasPermission()) fetch();
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        if (Boolean.TRUE.equals(result.get(android.Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(result.get(android.Manifest.permission.ACCESS_COARSE_LOCATION))) {
            fetch();
        } else {
            callback.onUnavailable("Location permission was not granted. Distances are hidden.");
        }
    }

    private void fetch() {
        if (helper == null) helper = new LocationHelper(fragment.requireContext());
        helper.currentLocation(fragment.requireContext(), new ResultCallback<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (fragment.isAdded()) callback.onLocation(new double[]{location.getLatitude(), location.getLongitude()});
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (fragment.isAdded()) callback.onUnavailable(error.getMessage() == null ? "Location unavailable." : error.getMessage());
            }
        });
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        if (helper != null) helper.cancel();
    }
}
