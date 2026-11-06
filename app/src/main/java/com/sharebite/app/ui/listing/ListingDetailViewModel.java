package com.sharebite.app.ui.listing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.remote.dto.WeatherInfo;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.Event;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.workers.BackgroundTaskRunner;

import java.util.List;

/** Listing details, reservation, provider management and the pickup-weather lookup. */
public class ListingDetailViewModel extends ViewModel {

    private final ServiceLocator services = ServiceLocator.get();
    private final BackgroundTaskRunner runner = new BackgroundTaskRunner("listing-detail", 1);

    private final MutableLiveData<UiState<FoodListing>> listing = new MutableLiveData<>(UiState.loading(null));
    private final MutableLiveData<User> provider = new MutableLiveData<>();
    private final MutableLiveData<UiState<WeatherInfo>> weather = new MutableLiveData<>();
    private final MutableLiveData<List<Reservation>> listingReservations = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private final MutableLiveData<Event<Reservation>> reserved = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> closed = new MutableLiveData<>();

    private Subscription subscription = Subscription.NONE;
    private String listingId;
    private boolean weatherRequested;
    private boolean providerRequested;

    public LiveData<UiState<FoodListing>> listing() { return listing; }
    public LiveData<User> provider() { return provider; }
    public LiveData<UiState<WeatherInfo>> weather() { return weather; }
    public LiveData<List<Reservation>> listingReservations() { return listingReservations; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<Event<String>> message() { return message; }
    public LiveData<Event<Reservation>> reserved() { return reserved; }
    public LiveData<Event<Boolean>> closed() { return closed; }

    public void start(@NonNull String id) {
        if (id.equals(listingId)) return;
        listingId = id;
        subscription = services.food().observeById(id, new ResultCallback<FoodListing>() {
            @Override
            public void onSuccess(FoodListing result) {
                if (result == null) {
                    listing.setValue(UiState.error("This listing is no longer available.", null));
                    return;
                }
                listing.setValue(UiState.success(result));
                loadProvider(result.getProviderId());
                loadWeather(result);
                User me = services.session().user();
                if (me != null && result.isOwnedBy(me.getUid())) refreshReservations();
            }

            @Override
            public void onError(@NonNull Exception e) {
                UiState<FoodListing> prev = listing.getValue();
                listing.setValue(UiState.error(ErrorMessages.from(e), prev == null ? null : prev.data));
            }
        });
    }

    @Nullable
    public FoodListing current() {
        UiState<FoodListing> s = listing.getValue();
        return s == null ? null : s.data;
    }

    private void loadProvider(String providerId) {
        if (providerRequested) return;
        providerRequested = true;
        services.users().getById(providerId, new ResultCallback<User>() {
            @Override public void onSuccess(User user) { provider.setValue(user); }
            @Override public void onError(@NonNull Exception e) { /* Listing still shows the stored provider name. */ }
        });
    }

    /** Weather near the pickup point (external HTTP API), fetched once on a background thread. */
    private void loadWeather(FoodListing l) {
        if (weatherRequested || !l.hasCoordinates()) return;
        weatherRequested = true;
        weather.setValue(UiState.loading(null));
        double lat = l.getLatitude(), lng = l.getLongitude();
        runner.submit(() -> services.weather().currentConditions(lat, lng), new ResultCallback<WeatherInfo>() {
            @Override public void onSuccess(WeatherInfo result) { weather.setValue(UiState.success(result)); }
            @Override public void onError(@NonNull Exception e) { weather.setValue(UiState.error(ErrorMessages.from(e), null)); }
        });
    }

    public void retryWeather() {
        FoodListing l = current();
        if (l == null) return;
        weatherRequested = false;
        loadWeather(l);
    }

    public void reserve(int quantity) {
        FoodListing l = current();
        User me = services.session().user();
        if (l == null || me == null || Boolean.TRUE.equals(busy.getValue())) return;
        busy.setValue(true);
        services.reservations().reserve(l.getListingId(), me, quantity, new ResultCallback<Reservation>() {
            @Override
            public void onSuccess(Reservation result) {
                busy.setValue(false);
                reserved.setValue(new Event<>(result));
            }

            @Override
            public void onError(@NonNull Exception e) {
                busy.setValue(false);
                message.setValue(new Event<>(ErrorMessages.from(e)));
            }
        });
    }

    public void refreshReservations() {
        FoodListing l = current();
        User me = services.session().user();
        if (l == null || me == null) return;
        services.reservations().getForListing(l.getListingId(), me.getUid(), new ResultCallback<List<Reservation>>() {
            @Override public void onSuccess(List<Reservation> result) { listingReservations.setValue(result); }
            @Override public void onError(@NonNull Exception e) { message.setValue(new Event<>(ErrorMessages.from(e))); }
        });
    }

    public void withdraw() {
        FoodListing l = current();
        if (l == null) return;
        run(cb -> services.food().cancelListing(l, cb), "Listing withdrawn. Anyone with an open reservation has been notified.", false);
    }

    public void markCompleted() {
        FoodListing l = current();
        if (l == null) return;
        run(cb -> services.food().markCompleted(l, cb), "Listing closed as completed.", false);
    }

    public void delete() {
        FoodListing l = current();
        if (l == null) return;
        run(cb -> services.food().deleteListing(l, cb), "Listing deleted.", true);
    }

    private interface Operation {
        void run(ResultCallback<Void> callback);
    }

    private void run(Operation op, String successMessage, boolean closeScreen) {
        if (Boolean.TRUE.equals(busy.getValue())) return;
        busy.setValue(true);
        op.run(new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                busy.setValue(false);
                message.setValue(new Event<>(successMessage));
                if (closeScreen) {
                    subscription.cancel();
                    closed.setValue(new Event<>(true));
                } else {
                    refreshReservations();
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                busy.setValue(false);
                message.setValue(new Event<>(ErrorMessages.from(e)));
            }
        });
    }

    @Override
    protected void onCleared() {
        subscription.cancel();
        runner.shutdown();
    }
}
