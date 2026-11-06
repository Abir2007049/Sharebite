package com.sharebite.app.ui.listing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharebite.app.R;
import com.sharebite.app.data.remote.dto.GeoPlace;
import com.sharebite.app.databinding.ActivityLocationPickerBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.ui.explore.MapSupport;
import com.sharebite.app.utils.LocationHelper;
import com.sharebite.app.utils.ViewUtils;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

/**
 * Lets a provider pin the pickup point: tap the map, search an address (Nominatim), or use the
 * device's current location. Returns coordinates plus the (editable) address.
 */
public class LocationPickerActivity extends BaseActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_ADDRESS = "address";

    private ActivityLocationPickerBinding binding;
    private LocationPickerViewModel viewModel;
    private Marker marker;
    private LocationHelper locationHelper;
    private ActivityResultLauncher<String[]> permissionLauncher;

    public static Intent intent(Context context, @Nullable Double lat, @Nullable Double lng, @Nullable String address) {
        Intent i = new Intent(context, LocationPickerActivity.class);
        if (lat != null && lng != null) {
            i.putExtra(EXTRA_LAT, lat);
            i.putExtra(EXTRA_LNG, lng);
        }
        i.putExtra(EXTRA_ADDRESS, address);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (requireUser() == null) return;
        binding = ActivityLocationPickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.topBar, true, false);
        applySystemBarInsets(binding.bottomPanel, false, true);
        setupToolbar(binding.toolbar, true);
        viewModel = new ViewModelProvider(this).get(LocationPickerViewModel.class);
        locationHelper = new LocationHelper(this);
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (LocationHelper.hasPermission(this)) useMyLocation();
            else showMessage(getString(R.string.location_permission_denied));
        });

        MapSupport.configure(binding.map);
        binding.map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                viewModel.setPin(p.getLatitude(), p.getLongitude(), true);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));

        if (savedInstanceState == null) {
            Intent in = getIntent();
            if (in.hasExtra(EXTRA_LAT)) {
                viewModel.setPin(in.getDoubleExtra(EXTRA_LAT, 0), in.getDoubleExtra(EXTRA_LNG, 0), false);
            }
            String address = in.getStringExtra(EXTRA_ADDRESS);
            if (address != null) {
                viewModel.setAddress(address);
                ViewUtils.setText(binding.searchInput, address);
            }
        }

        binding.searchInput.getEditText().setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        binding.searchInput.setEndIconOnClickListener(v -> search());
        binding.myLocationButton.setOnClickListener(v -> {
            if (LocationHelper.hasPermission(this)) useMyLocation();
            else permissionLauncher.launch(LocationHelper.PERMISSIONS);
        });
        binding.confirmButton.setOnClickListener(v -> confirm());

        viewModel.pin().observe(this, this::showPin);
        viewModel.address().observe(this, a -> binding.addressText.setText(a == null ? getString(R.string.no_address_yet) : a));
        viewModel.busy().observe(this, b -> ViewUtils.visible(binding.progress, b));
        viewModel.error().observe(this, e -> {
            String msg = e.consume();
            if (msg != null) showMessage(msg);
        });
        viewModel.searchResults().observe(this, e -> {
            List<GeoPlace> places = e.consume();
            if (places != null) chooseFrom(places);
        });
    }

    private void search() {
        String q = ViewUtils.text(binding.searchInput).trim();
        ViewUtils.hideKeyboard(this);
        viewModel.search(q);
    }

    private void chooseFrom(List<GeoPlace> places) {
        if (places.size() == 1) {
            viewModel.choose(places.get(0));
            return;
        }
        String[] labels = new String[places.size()];
        for (int i = 0; i < places.size(); i++) labels[i] = places.get(i).getDisplayName();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.choose_address)
                .setItems(labels, (d, which) -> viewModel.choose(places.get(which)))
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    private void useMyLocation() {
        ViewUtils.visible(binding.progress, true);
        locationHelper.currentLocation(this, new ResultCallback<Location>() {
            @Override
            public void onSuccess(Location location) {
                ViewUtils.visible(binding.progress, false);
                viewModel.setPin(location.getLatitude(), location.getLongitude(), true);
            }

            @Override
            public void onError(@NonNull Exception error) {
                ViewUtils.visible(binding.progress, false);
                showMessage(getString(R.string.location_unavailable));
            }
        });
    }

    private void showPin(@Nullable double[] latLng) {
        binding.confirmButton.setEnabled(latLng != null);
        if (latLng == null) return;
        GeoPoint point = new GeoPoint(latLng[0], latLng[1]);
        if (marker == null) {
            marker = new Marker(binding.map);
            marker.setIcon(MapSupport.markerIcon(this, R.color.forest_green));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setInfoWindow(null);
            binding.map.getOverlays().add(marker);
        }
        marker.setPosition(point);
        if (binding.map.getZoomLevelDouble() < 14) binding.map.getController().setZoom(16.0);
        binding.map.getController().animateTo(point);
        binding.map.invalidate();
    }

    private void confirm() {
        double[] p = viewModel.pin().getValue();
        if (p == null) return;
        Intent data = new Intent();
        data.putExtra(EXTRA_LAT, p[0]);
        data.putExtra(EXTRA_LNG, p[1]);
        data.putExtra(EXTRA_ADDRESS, viewModel.address().getValue());
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) binding.map.onResume();
    }

    @Override
    protected void onPause() {
        if (binding != null) binding.map.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (locationHelper != null) locationHelper.cancel();
        if (binding != null) binding.map.onDetach();
        super.onDestroy();
    }
}
