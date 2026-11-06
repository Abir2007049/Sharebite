package com.sharebite.app.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sharebite.app.data.remote.dto.GeoPlace;
import com.sharebite.app.data.remote.dto.OpenMeteoResponse;
import com.sharebite.app.data.remote.dto.WeatherInfo;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Gson parsing of the two external APIs. The JSON bodies follow the documented response formats
 * of Nominatim (format=jsonv2) and Open-Meteo (current=...).
 */
public class JsonParsingTest {

    private final HttpJsonClient client = new HttpJsonClient(new Gson(), "test");

    private static final String NOMINATIM_SEARCH = "[{\"place_id\":235614839,\"licence\":\"Data © OpenStreetMap contributors, ODbL 1.0.\","
            + "\"osm_type\":\"way\",\"osm_id\":24519203,\"lat\":\"23.7337927\",\"lon\":\"90.3927516\",\"category\":\"amenity\","
            + "\"type\":\"university\",\"place_rank\":30,\"importance\":0.52,\"addresstype\":\"amenity\",\"name\":\"University of Dhaka\","
            + "\"display_name\":\"University of Dhaka, Nilkhet Road, Dhaka, Bangladesh\",\"boundingbox\":[\"23.72\",\"23.74\",\"90.38\",\"90.40\"]},"
            + "{\"place_id\":1,\"lat\":\"not-a-number\",\"lon\":\"90\",\"display_name\":\"Broken\"}]";

    private static final String OPEN_METEO = "{\"latitude\":23.75,\"longitude\":90.375,\"generationtime_ms\":0.03,"
            + "\"utc_offset_seconds\":21600,\"timezone\":\"Asia/Dhaka\",\"timezone_abbreviation\":\"GMT+6\",\"elevation\":9.0,"
            + "\"current_units\":{\"time\":\"iso8601\",\"interval\":\"seconds\",\"temperature_2m\":\"°C\",\"relative_humidity_2m\":\"%\",\"weather_code\":\"wmo code\"},"
            + "\"current\":{\"time\":\"2026-11-07T14:00\",\"interval\":900,\"temperature_2m\":31.4,\"relative_humidity_2m\":66,\"weather_code\":2}}";

    @Test
    public void parsesNominatimResultsIntoModels() throws Exception {
        Type listType = new TypeToken<List<GeoPlace>>() { }.getType();
        List<GeoPlace> places = client.parse(NOMINATIM_SEARCH, listType);
        assertEquals(2, places.size());
        GeoPlace first = places.get(0);
        assertTrue(first.hasValidCoordinates());
        assertEquals(23.7337927, first.latitude(), 1e-9);
        assertEquals(90.3927516, first.longitude(), 1e-9);
        assertEquals("University of Dhaka, Nilkhet Road, Dhaka, Bangladesh", first.getDisplayName());
        assertFalse("invalid coordinates are detected", places.get(1).hasValidCoordinates());
    }

    @Test
    public void parsesOpenMeteoCurrentConditions() throws Exception {
        OpenMeteoResponse response = client.parse(OPEN_METEO, OpenMeteoResponse.class);
        WeatherInfo info = OpenMeteoWeatherService.toWeatherInfo(response);
        assertEquals(31.4, info.getTemperatureCelsius(), 1e-9);
        assertEquals(66.0, info.getRelativeHumidity(), 1e-9);
        assertEquals("Partly cloudy", info.describe());
        assertEquals("2026-11-07T14:00", info.getObservedAt());
    }

    @Test
    public void reverseGeocodingErrorBodyIsRecognised() throws Exception {
        GeoPlace place = client.parse("{\"error\":\"Unable to geocode\"}", GeoPlace.class);
        assertEquals("Unable to geocode", place.getError());
    }

    @Test
    public void malformedJsonIsReportedAsInvalidResponse() {
        try {
            client.parse("<html>Service unavailable</html>", OpenMeteoResponse.class);
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.INVALID_RESPONSE, e.getKind());
        }
    }

    @Test
    public void missingWeatherFieldsAreRejected() throws Exception {
        OpenMeteoResponse response = client.parse("{\"latitude\":1,\"longitude\":2}", OpenMeteoResponse.class);
        try {
            OpenMeteoWeatherService.toWeatherInfo(response);
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.INVALID_RESPONSE, e.getKind());
        }
    }
}
