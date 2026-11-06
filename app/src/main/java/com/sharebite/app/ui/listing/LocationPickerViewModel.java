package com.sharebite.app.ui.listing;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.remote.dto.GeoPlace;
import com.sharebite.app.domain.interfaces.GeocodingService;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.Event;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.workers.BackgroundTaskRunner;

import java.util.List;

/**
 * Pin position and address lookups for the location picker. Geocoding is a blocking HTTP call,
 * so it runs on the ViewModel's executor; the task is cancelled if the screen goes away.
 */
public class LocationPickerViewModel extends ViewModel {

    private final GeocodingService geocoding = ServiceLocator.get().geocoding();
    private final BackgroundTaskRunner runner = new BackgroundTaskRunner("geocoding", 1);

    private final MutableLiveData<double[]> pin = new MutableLiveData<>();
    private final MutableLiveData<String> address = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<List<GeoPlace>>> searchResults = new MutableLiveData<>();

    public LiveData<double[]> pin() { return pin; }
    public LiveData<String> address() { return address; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<Event<String>> error() { return error; }
    public LiveData<Event<List<GeoPlace>>> searchResults() { return searchResults; }

    public void setPin(double lat, double lng, boolean lookUpAddress) {
        pin.setValue(new double[]{lat, lng});
        if (lookUpAddress) reverseGeocode(lat, lng);
    }

    public void setAddress(String value) {
        address.setValue(value);
    }

    public void search(String query) {
        busy.setValue(true);
        runner.cancelAll();
        runner.submit(() -> geocoding.search(query, 5), new ResultCallback<List<GeoPlace>>() {
            @Override
            public void onSuccess(List<GeoPlace> result) {
                busy.setValue(false);
                searchResults.setValue(new Event<>(result));
            }

            @Override
            public void onError(@NonNull Exception e) {
                busy.setValue(false);
                error.setValue(new Event<>(ErrorMessages.from(e)));
            }
        });
    }

    public void choose(@NonNull GeoPlace place) {
        pin.setValue(new double[]{place.latitude(), place.longitude()});
        address.setValue(place.getDisplayName());
    }

    private void reverseGeocode(double lat, double lng) {
        busy.setValue(true);
        runner.cancelAll();
        runner.submit(() -> geocoding.reverse(lat, lng), new ResultCallback<GeoPlace>() {
            @Override
            public void onSuccess(GeoPlace result) {
                busy.setValue(false);
                address.setValue(result.getDisplayName());
            }

            @Override
            public void onError(@NonNull Exception e) {
                // The pin is still valid without an address suggestion; the provider can type it.
                busy.setValue(false);
                error.setValue(new Event<>(ErrorMessages.from(e)));
            }
        });
    }

    @Override
    protected void onCleared() {
        runner.shutdown();
    }
}
