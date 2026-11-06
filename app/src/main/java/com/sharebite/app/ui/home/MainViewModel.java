package com.sharebite.app.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.model.AppNotification;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;
import com.sharebite.app.domain.service.ListingQuery;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.Event;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.UiState;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity-scoped state shared by the bottom-navigation tabs. It owns the realtime Firestore
 * listeners (listings, reservations, notifications) so switching tabs doesn't re-subscribe,
 * and cancels them all in {@link #onCleared()}.
 */
public class MainViewModel extends ViewModel {

    private final ServiceLocator services = ServiceLocator.get();
    private final List<Subscription> subscriptions = new ArrayList<>();

    private final MutableLiveData<UiState<List<FoodListing>>> availableListings = new MutableLiveData<>(UiState.loading(null));
    private final MutableLiveData<UiState<List<FoodListing>>> myListings = new MutableLiveData<>(UiState.loading(null));
    private final MutableLiveData<UiState<List<Reservation>>> reservations = new MutableLiveData<>(UiState.loading(null));
    private final MutableLiveData<List<AppNotification>> notifications = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<double[]> userLocation = new MutableLiveData<>();
    private final MutableLiveData<Event<ListingQuery>> exploreRequests = new MutableLiveData<>();

    private String startedForUid;
    private UserRole startedForRole;

    /** Starts the listeners appropriate for the user's role (idempotent). */
    public void start(@NonNull User user) {
        if (user.getUid().equals(startedForUid) && user.getRole() == startedForRole) return;
        stop();
        startedForUid = user.getUid();
        startedForRole = user.getRole();

        if (user.canCreateListings()) {
            subscriptions.add(services.food().observeProviderListings(user.getUid(), into(myListings)));
            subscriptions.add(services.reservations().observeForProvider(user.getUid(), into(reservations)));
            // Housekeeping: close this provider's listings whose pickup deadline has passed.
            services.food().expireOverdueListings(user.getUid(), new ResultCallback<Integer>() {
                @Override public void onSuccess(Integer count) { }
                @Override public void onError(@NonNull Exception error) { }
            });
        } else {
            subscriptions.add(services.food().observeAvailableListings(into(availableListings)));
            subscriptions.add(services.reservations().observeForReceiver(user.getUid(), into(reservations)));
        }
        subscriptions.add(services.notifications().observeForRecipient(user.getUid(), new ResultCallback<List<AppNotification>>() {
            @Override public void onSuccess(List<AppNotification> result) { notifications.setValue(result); }
            @Override public void onError(@NonNull Exception error) { }
        }));
    }

    public LiveData<UiState<List<FoodListing>>> availableListings() { return availableListings; }
    public LiveData<UiState<List<FoodListing>>> myListings() { return myListings; }
    public LiveData<UiState<List<Reservation>>> reservations() { return reservations; }
    public LiveData<List<AppNotification>> notifications() { return notifications; }
    public LiveData<double[]> userLocation() { return userLocation; }

    public void setUserLocation(@Nullable double[] latLng) {
        userLocation.setValue(latLng);
    }

    /** Home sections ask the Explore tab to open with a preset filter. */
    public LiveData<Event<ListingQuery>> exploreRequests() { return exploreRequests; }

    public void requestExplore(@NonNull ListingQuery query) {
        exploreRequests.setValue(new Event<>(query));
    }

    public int unreadCount() {
        int count = 0;
        List<AppNotification> list = notifications.getValue();
        if (list != null) for (AppNotification n : list) if (!n.isRead()) count++;
        return count;
    }

    private <T> ResultCallback<List<T>> into(MutableLiveData<UiState<List<T>>> target) {
        return new ResultCallback<List<T>>() {
            @Override
            public void onSuccess(List<T> result) {
                target.setValue(UiState.success(result));
            }

            @Override
            public void onError(@NonNull Exception error) {
                UiState<List<T>> previous = target.getValue();
                target.setValue(UiState.error(ErrorMessages.from(error), previous == null ? null : previous.data));
            }
        };
    }

    private void stop() {
        for (Subscription s : subscriptions) s.cancel();
        subscriptions.clear();
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
