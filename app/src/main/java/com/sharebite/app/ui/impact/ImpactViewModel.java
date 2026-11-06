package com.sharebite.app.ui.impact;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.service.ImpactCalculator;
import com.sharebite.app.domain.service.ImpactReport;
import com.sharebite.app.workers.BackgroundTaskRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Future;

/** Aggregates the user's reservation history into an {@link ImpactReport} on a worker thread. */
public class ImpactViewModel extends ViewModel {

    private final BackgroundTaskRunner runner = new BackgroundTaskRunner("impact", 1);
    private final MutableLiveData<ImpactReport> report = new MutableLiveData<>();
    private Future<?> pending;

    public LiveData<ImpactReport> report() {
        return report;
    }

    public void recalculate(@NonNull UserRole role, @Nullable List<Reservation> reservations, @Nullable List<FoodListing> listings) {
        if (reservations == null) return;
        if (pending != null) pending.cancel(true);
        List<Reservation> r = new ArrayList<>(reservations);
        List<FoodListing> l = listings == null ? new ArrayList<>() : new ArrayList<>(listings);
        pending = runner.submit(() -> ImpactCalculator.calculate(role, r, l, new Date(), TimeZone.getDefault(), Locale.getDefault()),
                new ResultCallback<ImpactReport>() {
                    @Override public void onSuccess(ImpactReport result) { report.setValue(result); }
                    @Override public void onError(@NonNull Exception error) { }
                });
    }

    @Override
    protected void onCleared() {
        runner.shutdown();
    }
}
