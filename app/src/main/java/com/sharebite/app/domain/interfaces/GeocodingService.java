package com.sharebite.app.domain.interfaces;

import com.sharebite.app.data.remote.ApiException;
import com.sharebite.app.data.remote.dto.GeoPlace;

import java.util.List;

/** Address lookup over HTTP. Methods block and must be called from a background thread. */
public interface GeocodingService {

    List<GeoPlace> search(String query, int limit) throws ApiException;

    GeoPlace reverse(double latitude, double longitude) throws ApiException;
}
