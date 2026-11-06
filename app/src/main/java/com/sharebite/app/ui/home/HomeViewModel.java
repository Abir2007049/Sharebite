package com.sharebite.app.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.workers.BackgroundTaskRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

/** Builds the home feed off the main thread whenever listings or the user's location change. */
public class HomeViewModel extends ViewModel {

    private final BackgroundTaskRunner runner = new BackgroundTaskRunner("home-feed", 1);
    private final MutableLiveData<HomeFeed> feed = new MutableLiveData<>();
    private Future<?> pending;

    public LiveData<HomeFeed> feed() {
        return feed;
    }

    public void rebuild(@Nullable List<FoodListing> listings, @Nullable User user, @Nullable double[] location) {
        if (listings == null) return;
        // Only the newest input matters; drop any computation still running for older data.
        if (pending != null) pending.cancel(true);
        List<FoodListing> snapshot = new ArrayList<>(listings);
        pending = runner.submit(() -> HomeFeed.build(snapshot, user, location, new Date()), new ResultCallback<HomeFeed>() {
            @Override
            public void onSuccess(HomeFeed result) {
                feed.setValue(result);
            }

            @Override
            public void onError(@NonNull Exception error) {
                // Pure in-memory computation; a failure here means a cancelled task.
            }
        });
    }

    @Override
    protected void onCleared() {
        runner.shutdown();
    }
}
