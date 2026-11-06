package com.sharebite.app.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.gson.reflect.TypeToken;
import com.sharebite.app.data.remote.dto.GeoPlace;
import com.sharebite.app.domain.interfaces.GeocodingService;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Geocoding via the public OpenStreetMap Nominatim API (no API key).
 * Usage policy: identify the app via User-Agent and keep to ≤1 request/second — the UI only
 * calls this on explicit user action ("Find on map"), never per keystroke.
 */
public class NominatimGeocodingService implements GeocodingService {

    private static final String BASE_URL = "https://nominatim.openstreetmap.org";
    private static final Type PLACE_LIST = new TypeToken<List<GeoPlace>>() { }.getType();

    private final HttpJsonClient client;

    public NominatimGeocodingService(@NonNull HttpJsonClient client) {
        this.client = client;
    }

    @WorkerThread
    @Override
    public List<GeoPlace> search(String query, int limit) throws ApiException {
        if (query == null || query.trim().length() < 3) {
            throw new ApiException(ApiException.Kind.NO_RESULTS, "Enter at least 3 characters of the address.");
        }
        String url = BASE_URL + "/search?format=jsonv2&limit=" + Math.max(1, Math.min(limit, 10))
                + "&q=" + encode(query.trim());
        List<GeoPlace> raw = client.get(url, PLACE_LIST);
        List<GeoPlace> valid = new ArrayList<>();
        for (GeoPlace place : raw) {
            if (place != null && place.hasValidCoordinates()) valid.add(place);
        }
        if (valid.isEmpty()) {
            throw new ApiException(ApiException.Kind.NO_RESULTS, "No matching address found. Try adding the area or city.");
        }
        return valid;
    }

    @WorkerThread
    @Override
    public GeoPlace reverse(double latitude, double longitude) throws ApiException {
        String url = String.format(Locale.US, "%s/reverse?format=jsonv2&lat=%.6f&lon=%.6f", BASE_URL, latitude, longitude);
        GeoPlace place = client.get(url, GeoPlace.class);
        if (place.getError() != null || place.getDisplayName() == null) {
            throw new ApiException(ApiException.Kind.NO_RESULTS, "No address found for this location.");
        }
        return place;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always supported.
        }
    }
}
