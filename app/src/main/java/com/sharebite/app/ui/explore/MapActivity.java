package com.sharebite.app.ui.explore;

import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.sharebite.app.R;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.databinding.ActivityMapBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.ui.listing.ListingDetailActivity;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.LocationHelper;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** All reservable listings with a pickup location, on an OpenStreetMap map. */
public class MapActivity extends BaseActivity {

    private ActivityMapBinding binding;
    private final List<Marker> listingMarkers = new ArrayList<>();
    private Marker userMarker;
    private LocationHelper locationHelper;
    private boolean framed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (requireUser() == null) return;
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.topBar, true, false);
        applySystemBarInsets(binding.bottomArea, false, true);
        setupToolbar(binding.toolbar, true);
        MapSupport.configure(binding.map);

        binding.closeCard.setOnClickListener(v -> ViewUtils.visible(binding.listingCard, false));
        MapViewModel viewModel = new ViewModelProvider(this).get(MapViewModel.class);
        viewModel.listings().observe(this, this::render);

        // Show the user's own position only if they have already granted location access.
        if (LocationHelper.hasPermission(this)) {
            locationHelper = new LocationHelper(this);
            locationHelper.currentLocation(this, new ResultCallback<Location>() {
                @Override
                public void onSuccess(Location location) {
                    showUser(new GeoPoint(location.getLatitude(), location.getLongitude()));
                }

                @Override
                public void onError(@NonNull Exception error) { }
            });
        }
    }

    private void render(UiState<List<FoodListing>> state) {
        ViewUtils.visible(binding.progress, state.isLoading());
        if (state.message != null) showMessage(state.message);
        if (state.data == null) return;

        binding.map.getOverlays().removeAll(listingMarkers);
        listingMarkers.clear();
        for (FoodListing l : state.data) {
            Marker m = new Marker(binding.map);
            m.setPosition(new GeoPoint(l.getLatitude(), l.getLongitude()));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setIcon(MapSupport.markerIcon(this, R.color.forest_green));
            m.setTitle(l.getTitle());
            m.setOnMarkerClickListener((marker, map) -> {
                showCard(l);
                map.getController().animateTo(marker.getPosition());
                return true;
            });
            listingMarkers.add(m);
        }
        binding.map.getOverlays().addAll(listingMarkers);
        binding.map.invalidate();
        binding.emptyText.setVisibility(state.data.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        if (!framed && !state.data.isEmpty()) {
            framed = true;
            List<GeoPoint> points = new ArrayList<>();
            for (Marker m : listingMarkers) points.add(m.getPosition());
            if (points.size() == 1) {
                binding.map.getController().setZoom(15.0);
                binding.map.getController().setCenter(points.get(0));
            } else {
                BoundingBox box = BoundingBox.fromGeoPointsSafe(points);
                binding.map.post(() -> binding.map.zoomToBoundingBox(box.increaseByScale(1.3f), false, 64));
            }
        }
    }

    private void showUser(GeoPoint point) {
        if (userMarker == null) {
            userMarker = new Marker(binding.map);
            userMarker.setIcon(MapSupport.markerIcon(this, R.color.info_blue));
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            userMarker.setTitle(getString(R.string.you_are_here));
            binding.map.getOverlays().add(userMarker);
        }
        userMarker.setPosition(point);
        if (!framed) {
            binding.map.getController().setZoom(14.0);
            binding.map.getController().setCenter(point);
        }
        binding.map.invalidate();
    }

    private void showCard(FoodListing l) {
        ViewUtils.visible(binding.listingCard, true);
        ImageLoader.loadFood(binding.cardImage, l.getImageUrl());
        binding.cardTitle.setText(l.getTitle());
        ViewUtils.styleDistributionBadge(binding.cardBadge, l.getDistributionType());
        binding.cardMeta.setText(getString(R.string.map_card_meta,
                Formatters.quantity(this, l.getAvailableQuantity(), l.getUnit()),
                Formatters.timeLeft(this, l.getPickupDeadline(), new Date())));
        binding.cardOpen.setOnClickListener(v -> startActivity(ListingDetailActivity.intent(this, l.getListingId())));
        binding.cardDirections.setOnClickListener(v ->
                MapSupport.openNavigation(this, l.getLatitude(), l.getLongitude(), l.getTitle()));
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
