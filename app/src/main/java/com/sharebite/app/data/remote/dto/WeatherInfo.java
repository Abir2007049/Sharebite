package com.sharebite.app.data.remote.dto;

import androidx.annotation.NonNull;

/** Current conditions at a pickup location, as shown to the user. */
public class WeatherInfo {

    private final double temperatureCelsius;
    private final Double relativeHumidity;
    private final int weatherCode;
    private final String observedAt;

    public WeatherInfo(double temperatureCelsius, Double relativeHumidity, int weatherCode, String observedAt) {
        this.temperatureCelsius = temperatureCelsius;
        this.relativeHumidity = relativeHumidity;
        this.weatherCode = weatherCode;
        this.observedAt = observedAt;
    }

    public double getTemperatureCelsius() { return temperatureCelsius; }
    public Double getRelativeHumidity() { return relativeHumidity; }
    public int getWeatherCode() { return weatherCode; }
    public String getObservedAt() { return observedAt; }

    /** Short description of a WMO weather interpretation code (as used by Open-Meteo). */
    @NonNull
    public String describe() {
        int c = weatherCode;
        if (c == 0) return "Clear sky";
        if (c <= 3) return "Partly cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Rain showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorm";
        return "Unknown conditions";
    }

    public boolean isWet() {
        int c = weatherCode;
        return (c >= 51 && c <= 67) || (c >= 80 && c <= 82) || c >= 95;
    }
}
