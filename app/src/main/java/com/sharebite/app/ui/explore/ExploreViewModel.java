package com.sharebite.app.ui.explore;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.service.ListingItem;
import com.sharebite.app.domain.service.ListingQuery;
import com.sharebite.app.domain.service.ListingQueryEngine;
import com.sharebite.app.workers.BackgroundTaskRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Search/filter/sort state for Explore. Filtering runs on a background thread; typing is
 * debounced so a burst of keystrokes triggers one computation.
 */
public class ExploreViewModel extends ViewModel {

    private static final long SEARCH_DEBOUNCE_MS = 250;

    private final BackgroundTaskRunner runner = new BackgroundTaskRunner("explore", 1);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<ListingQuery> query = new MutableLiveData<>(ListingQuery.defaults());
    private final MutableLiveData<List<ListingItem>> results = new MutableLiveData<>();

    private List<FoodListing> source;
    private User viewer;
    private double[] location;
    private Future<?> pending;
    private final Runnable recompute = this::recompute;

    public LiveData<ListingQuery> query() { return query; }
    public LiveData<List<ListingItem>> results() { return results; }

    public ListingQuery currentQuery() {
        ListingQuery q = query.getValue();
        return q == null ? ListingQuery.defaults() : q;
    }

    public void setInputs(@Nullable List<FoodListing> listings, @Nullable User user, @Nullable double[] userLocation) {
        if (listings != null) source = new ArrayList<>(listings);
        viewer = user;
        location = userLocation;
        schedule(0);
    }

    public void setQuery(@NonNull ListingQuery newQuery) {
        boolean textOnly = newQuery.category == currentQuery().category
                && newQuery.distributionType == currentQuery().distributionType
                && newQuery.sort == currentQuery().sort;
        query.setValue(newQuery);
        schedule(textOnly ? SEARCH_DEBOUNCE_MS : 0);
    }

    public boolean hasLocation() {
        return location != null;
    }

    private void schedule(long delayMs) {
        handler.removeCallbacks(recompute);
        handler.postDelayed(recompute, delayMs);
    }

    private void recompute() {
        if (source == null) return;
        if (pending != null) pending.cancel(true);
        List<FoodListing> listings = source;
        ListingQuery q = currentQuery();
        User u = viewer;
        double[] loc = location;
        pending = runner.submit(() -> ListingQueryEngine.apply(listings, q, u, loc, new Date()), new ResultCallback<List<ListingItem>>() {
            @Override
            public void onSuccess(List<ListingItem> result) {
                results.setValue(result);
            }

            @Override
            public void onError(@NonNull Exception error) {
                // In-memory work; only fails when cancelled by a newer query.
            }
        });
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacks(recompute);
        runner.shutdown();
    }
}
