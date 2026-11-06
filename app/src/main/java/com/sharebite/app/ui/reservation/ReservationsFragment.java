package com.sharebite.app.ui.reservation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sharebite.app.R;
import com.sharebite.app.adapter.ReservationAdapter;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.databinding.FragmentReservationsBinding;
import com.sharebite.app.domain.service.ReservationActions;
import com.sharebite.app.ui.BaseTabFragment;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Reservations for the signed-in user. Providers see Pending / Confirmed / History (incoming
 * requests); receivers and charities see Pending / Confirmed / History of their own requests.
 */
public class ReservationsFragment extends BaseTabFragment {

    private static final String STATE_FILTER = "filter";

    private FragmentReservationsBinding binding;
    private ReservationAdapter adapter;
    private final ReservationActionController actions = new ReservationActionController();
    private int filterId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentReservationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        User user = currentUser();
        if (user == null) return;
        boolean provider = user.getRole() == UserRole.PROVIDER;
        int title = provider ? R.string.reservations_title_provider
                : user.getRole() == UserRole.CHARITY ? R.string.reservations_title_charity : R.string.reservations_title_receiver;
        int subtitle = provider ? R.string.reservations_subtitle_provider : R.string.reservations_subtitle_consumer;
        bindHeader(binding.header, getString(title), getString(subtitle));

        binding.filterFirst.setText(provider ? R.string.filter_requests : R.string.filter_pending);
        binding.filterSecond.setText(R.string.filter_confirmed);
        binding.filterThird.setText(R.string.filter_history);
        filterId = savedInstanceState == null ? R.id.filterFirst : savedInstanceState.getInt(STATE_FILTER, R.id.filterFirst);
        binding.filterToggle.check(filterId);
        binding.filterToggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            filterId = id;
            render(mainViewModel().reservations().getValue());
        });

        adapter = new ReservationAdapter(user.getUid(), new ReservationAdapter.Listener() {
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
                        if (binding != null) ViewUtils.visible(binding.actionProgress, busy);
                    }

                    @Override
                    public void onResult(@NonNull String message, boolean success) {
                        showMessage(message);
                    }
                });
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        mainViewModel().reservations().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILTER, filterId);
    }

    private void render(@Nullable UiState<List<Reservation>> state) {
        if (state == null || binding == null) return;
        ViewUtils.visible(binding.progress, state.isLoading() && state.data == null);
        if (state.message != null) showMessage(state.message);
        if (state.data == null) return;

        List<Reservation> shown = new ArrayList<>();
        for (Reservation r : state.data) {
            ReservationStatus s = r.getStatus();
            boolean match = filterId == R.id.filterFirst ? s == ReservationStatus.PENDING
                    : filterId == R.id.filterSecond ? s == ReservationStatus.CONFIRMED
                    : s != null && s.isTerminal();
            if (match) shown.add(r);
        }
        adapter.submitList(shown);

        boolean empty = shown.isEmpty();
        ViewUtils.visible(binding.emptyState.getRoot(), empty);
        if (empty) {
            User user = currentUser();
            boolean provider = user != null && user.getRole() == UserRole.PROVIDER;
            binding.emptyState.emptyTitle.setText(R.string.empty_reservations_title);
            binding.emptyState.emptyMessage.setText(filterId == R.id.filterThird ? R.string.empty_history_message
                    : provider ? R.string.empty_reservations_provider : R.string.empty_reservations_consumer);
            ViewUtils.visible(binding.emptyState.emptyAction, !provider && filterId != R.id.filterThird);
            binding.emptyState.emptyAction.setText(R.string.find_food);
            binding.emptyState.emptyAction.setOnClickListener(v -> selectTab(R.id.nav_explore));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
