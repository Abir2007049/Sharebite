package com.sharebite.app.ui.explore;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.sharebite.app.R;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;

import java.util.Locale;

/** Shared map setup and the hand-off to an external navigation app. */
public final class MapSupport {

    /** Used only to frame the map before any real location is known; never treated as the user's position. */
    static final GeoPoint WORLD_CENTER = new GeoPoint(20.0, 0.0);

    private MapSupport() {
    }

    public static void configure(@NonNull MapView map) {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(3.0);
        map.getController().setZoom(3.0);
        map.getController().setCenter(WORLD_CENTER);
    }

    public static Drawable markerIcon(@NonNull Context c, @ColorRes int color) {
        Drawable d = DrawableCompat.wrap(ContextCompat.getDrawable(c, R.drawable.ic_place).mutate());
        DrawableCompat.setTint(d, ContextCompat.getColor(c, color));
        return d;
    }

    /**
     * Opens turn-by-turn directions in whatever maps app is installed (geo: URI), falling back to
     * OpenStreetMap in the browser.
     */
    public static void openNavigation(@NonNull Context context, double lat, double lng, @NonNull String label) {
        Uri geo = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", lat, lng, lat, lng, Uri.encode(label)));
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, geo));
        } catch (ActivityNotFoundException e) {
            Uri web = Uri.parse(String.format(Locale.US,
                    "https://www.openstreetmap.org/directions?route=%%3B%f%%2C%f#map=16/%f/%f", lat, lng, lat, lng));
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, web));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, R.string.no_maps_app, Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Opens a text address search in an external maps app (used when a listing has no coordinates). */
    public static void openAddressSearch(@NonNull Context context, @NonNull String address) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_maps_app, Toast.LENGTH_LONG).show();
        }
    }
}
