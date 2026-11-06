package com.sharebite.app.ui.explore;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.UiState;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Reservable listings that have coordinates, for the map screen. */
public class MapViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<FoodListing>>> listings = new MutableLiveData<>(UiState.loading(null));
    private final Subscription subscription;

    public MapViewModel() {
        subscription = ServiceLocator.get().food().observeAvailableListings(new ResultCallback<List<FoodListing>>() {
            @Override
            public void onSuccess(List<FoodListing> result) {
                Date now = new Date();
                List<FoodListing> mappable = new ArrayList<>();
                for (FoodListing l : result) if (l.hasCoordinates() && l.isReservableAt(now)) mappable.add(l);
                listings.setValue(UiState.success(mappable));
            }

            @Override
            public void onError(@NonNull Exception error) {
                listings.setValue(UiState.error(ErrorMessages.from(error), null));
            }
        });
    }

    public LiveData<UiState<List<FoodListing>>> listings() {
        return listings;
    }

    @Override
    protected void onCleared() {
        subscription.cancel();
    }
}
