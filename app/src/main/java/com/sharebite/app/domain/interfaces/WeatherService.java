package com.sharebite.app.domain.interfaces;

import com.sharebite.app.data.remote.ApiException;
import com.sharebite.app.data.remote.dto.WeatherInfo;

/** Current conditions at a location over HTTP. Blocks; call from a background thread. */
public interface WeatherService {

    WeatherInfo currentConditions(double latitude, double longitude) throws ApiException;
}
