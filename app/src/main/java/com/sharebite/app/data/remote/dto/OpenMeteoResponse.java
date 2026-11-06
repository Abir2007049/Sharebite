package com.sharebite.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Raw response of the Open-Meteo forecast API with {@code current=temperature_2m,
 * relative_humidity_2m,weather_code}. Mapped to {@link WeatherInfo} by the service.
 */
public class OpenMeteoResponse {

    @SerializedName("latitude") public double latitude;
    @SerializedName("longitude") public double longitude;
    @SerializedName("timezone") public String timezone;
    @SerializedName("current") public Current current;

    public static class Current {
        @SerializedName("time") public String time;
        @SerializedName("temperature_2m") public Double temperature;
        @SerializedName("relative_humidity_2m") public Double relativeHumidity;
        @SerializedName("weather_code") public Integer weatherCode;
    }
}
