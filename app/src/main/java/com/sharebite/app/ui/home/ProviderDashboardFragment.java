package com.sharebite.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sharebite.app.R;
import com.sharebite.app.adapter.ReservationAdapter;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.FragmentProviderDashboardBinding;
import com.sharebite.app.domain.service.ReservationActions;
import com.sharebite.app.ui.BaseTabFragment;
import com.sharebite.app.ui.listing.ListingEditorActivity;
import com.sharebite.app.ui.reservation.ReservationActionController;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Provider home: quick "share food" action, headline stats, pending requests, recent activity. */
public class ProviderDashboardFragment extends BaseTabFragment {

    private static final int PREVIEW_SIZE = 3;
    private static final int RECENT_SIZE = 5;

    private FragmentProviderDashboardBinding binding;
    private final ReservationActionController actions = new ReservationActionController();
    private ReservationAdapter pendingAdapter;
    private ReservationAdapter recentAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProviderDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        User user = currentUser();
        if (user == null) return;
        showBrand(binding.header);
        bindHeader(binding.header, getString(com.sharebite.app.utils.Formatters.greetingFor(
                java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)), user.displayName()), getString(R.string.dashboard_subtitle));

        ReservationAdapter.Listener listener = new ReservationAdapter.Listener() {
            @Override
            public void onOpen(@NonNull Reservation reservation) {
                openReservation(reservation);
            }

            @Override
            public void onAction(@NonNull Reservation reservation, @NonNull ReservationActions.Action action) {
                User actor = currentUser();
                if (actor == null) return;
                actions.request(requireContext(), reservation, action, actor, new ReservationActionController.Listener() {
                    @Override
                    public void onBusy(boolean busy) {
                        if (binding != null) ViewUtils.visible(binding.progress, busy);
                    }

                    @Override
                    public void onResult(@NonNull String message, boolean success) {
                        showMessage(message);
                    }
                });
            }
        };
        pendingAdapter = new ReservationAdapter(user.getUid(), listener);
        recentAdapter = new ReservationAdapter(user.getUid(), listener);
        binding.pendingList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.pendingList.setAdapter(pendingAdapter);
        binding.recentList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recentList.setAdapter(recentAdapter);

        binding.statActive.statLabel.setText(R.string.stat_active_listings);
        binding.statShared.statLabel.setText(R.string.stat_quantity_shared);
        binding.statPending.statLabel.setText(R.string.stat_pending_requests);
        binding.statCompleted.statLabel.setText(R.string.stat_completed_pickups);

        binding.shareFoodButton.setOnClickListener(v -> startActivity(ListingEditorActivity.createIntent(requireContext())));
        binding.seeAllPending.setOnClickListener(v -> selectTab(R.id.nav_reservations));

        mainViewModel().myListings().observe(getViewLifecycleOwner(), this::renderListings);
        mainViewModel().reservations().observe(getViewLifecycleOwner(), this::renderReservations);
    }

    private void renderListings(UiState<List<FoodListing>> state) {
        int active = 0;
        Date now = new Date();
        if (state.data != null) {
            for (FoodListing l : state.data) {
                ListingStatus s = l.effectiveStatus(now);
                if (s == ListingStatus.AVAILABLE || s == ListingStatus.FULLY_RESERVED) active++;
            }
        }
        binding.statActive.statValue.setText(String.valueOf(active));
    }

    private void renderReservations(UiState<List<Reservation>> state) {
        ViewUtils.visible(binding.progress, state.isLoading() && state.data == null);
        if (state.message != null) showMessage(state.message);
        List<Reservation> all = state.data == null ? new ArrayList<>() : state.data;

        List<Reservation> pending = new ArrayList<>();
        List<Reservation> recent = new ArrayList<>();
        int shared = 0, completed = 0;
        for (Reservation r : all) {
            if (r.getStatus() == ReservationStatus.PENDING) {
                pending.add(r);
            } else if (recent.size() < RECENT_SIZE) {
                recent.add(r);
            }
            if (r.getStatus() == ReservationStatus.COLLECTED) {
                shared += r.getQuantity();
                completed++;
            }
        }
        binding.statShared.statValue.setText(String.valueOf(shared));
        binding.statPending.statValue.setText(String.valueOf(pending.size()));
        binding.statCompleted.statValue.setText(String.valueOf(completed));

        pendingAdapter.submitList(pending.size() > PREVIEW_SIZE ? new ArrayList<>(pending.subList(0, PREVIEW_SIZE)) : pending);
        ViewUtils.visible(binding.pendingEmpty, pending.isEmpty() && state.data != null);
        ViewUtils.visible(binding.seeAllPending, pending.size() > PREVIEW_SIZE);
        recentAdapter.submitList(recent);
        ViewUtils.visible(binding.recentEmpty, recent.isEmpty() && state.data != null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
