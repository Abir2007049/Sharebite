package com.sharebite.app.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.sharebite.app.data.remote.dto.OpenMeteoResponse;
import com.sharebite.app.data.remote.dto.WeatherInfo;
import com.sharebite.app.domain.interfaces.WeatherService;

import java.util.Locale;

/** Current temperature/conditions from the free Open-Meteo API (no API key required). */
public class OpenMeteoWeatherService implements WeatherService {

    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";

    private final HttpJsonClient client;

    public OpenMeteoWeatherService(@NonNull HttpJsonClient client) {
        this.client = client;
    }

    @WorkerThread
    @Override
    public WeatherInfo currentConditions(double latitude, double longitude) throws ApiException {
        String url = String.format(Locale.US,
                "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,weather_code&timezone=auto",
                BASE_URL, latitude, longitude);
        OpenMeteoResponse response = client.get(url, OpenMeteoResponse.class);
        return toWeatherInfo(response);
    }

    /** Validates the DTO and converts it to the app model. Package-private for unit tests. */
    static WeatherInfo toWeatherInfo(OpenMeteoResponse response) throws ApiException {
        if (response.current == null || response.current.temperature == null) {
            throw new ApiException(ApiException.Kind.INVALID_RESPONSE, "Weather data was missing from the response.");
        }
        int code = response.current.weatherCode == null ? -1 : response.current.weatherCode;
        return new WeatherInfo(response.current.temperature, response.current.relativeHumidity, code, response.current.time);
    }
}
