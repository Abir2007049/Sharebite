package com.sharebite.app.data.remote.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * One result from the Nominatim (OpenStreetMap) geocoding API (format=jsonv2).
 * Nominatim returns coordinates as strings, so they are parsed on access.
 */
public class GeoPlace {

    @SerializedName("place_id") private long placeId;
    @SerializedName("lat") private String lat;
    @SerializedName("lon") private String lon;
    @SerializedName("display_name") private String displayName;
    @SerializedName("name") private String name;
    @SerializedName("category") private String category;
    @SerializedName("type") private String type;
    /** Present instead of a place when reverse geocoding fails. */
    @SerializedName("error") private String error;

    public GeoPlace() {
    }

    /** Test/helper constructor. */
    public GeoPlace(String lat, String lon, String displayName) {
        this.lat = lat;
        this.lon = lon;
        this.displayName = displayName;
    }

    public long getPlaceId() { return placeId; }
    public String getDisplayName() { return displayName; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getType() { return type; }
    public String getError() { return error; }

    public boolean hasValidCoordinates() {
        try {
            double la = Double.parseDouble(lat);
            double lo = Double.parseDouble(lon);
            return la >= -90 && la <= 90 && lo >= -180 && lo <= 180;
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    public double latitude() { return Double.parseDouble(lat); }
    public double longitude() { return Double.parseDouble(lon); }

    @NonNull
    @Override
    public String toString() {
        return displayName == null ? "" : displayName;
    }
}
