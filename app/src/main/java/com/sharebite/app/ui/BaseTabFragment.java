package com.sharebite.app.ui;

import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.ViewScreenHeaderBinding;
import com.sharebite.app.ui.home.MainActivity;
import com.sharebite.app.ui.home.MainViewModel;
import com.sharebite.app.ui.listing.ListingDetailActivity;
import com.sharebite.app.ui.profile.NotificationsActivity;
import com.sharebite.app.ui.reservation.ReservationDetailActivity;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.ViewUtils;

/** Common helpers for the bottom-navigation tabs hosted by {@link MainActivity}. */
public abstract class BaseTabFragment extends Fragment {

    protected MainViewModel mainViewModel() {
        return new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }

    @Nullable
    protected User currentUser() {
        return ServiceLocator.get().session().user();
    }

    /** Wires the shared header: title, subtitle and the notifications bell with unread badge. */
    protected void bindHeader(@NonNull ViewScreenHeaderBinding header, @NonNull CharSequence title, @Nullable CharSequence subtitle) {
        header.headerTitle.setText(title);
        ViewUtils.visible(header.headerSubtitle, subtitle != null);
        if (subtitle != null) header.headerSubtitle.setText(subtitle);
        if (ServiceLocator.get().session().isGuest()) {
            // Notifications belong to an account; guests have none.
            ((View) header.notificationsButton.getParent()).setVisibility(View.GONE);
            return;
        }
        header.notificationsButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), NotificationsActivity.class)));
        mainViewModel().notifications().observe(getViewLifecycleOwner(), list -> {
            int unread = mainViewModel().unreadCount();
            ViewUtils.visible(header.unreadBadge, unread > 0);
            header.unreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            header.notificationsButton.setContentDescription(getString(com.sharebite.app.R.string.notifications_with_count, unread));
        });
    }

    protected void openListing(@NonNull FoodListing listing) {
        startActivity(ListingDetailActivity.intent(requireContext(), listing.getListingId()));
    }

    protected void openReservation(@NonNull Reservation reservation) {
        startActivity(ReservationDetailActivity.intent(requireContext(), reservation.getReservationId()));
    }

    protected void selectTab(int tabId) {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).selectTab(tabId);
    }

    protected void showMessage(@NonNull String message) {
        View v = getView();
        if (v != null) Snackbar.make(v, message, Snackbar.LENGTH_LONG).show();
    }
}
