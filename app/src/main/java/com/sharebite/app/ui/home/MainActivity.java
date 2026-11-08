package com.sharebite.app.ui.home;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.badge.BadgeDrawable;
import com.sharebite.app.R;
import com.sharebite.app.data.model.AppNotification;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.databinding.ActivityMainBinding;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.ui.explore.ExploreFragment;
import com.sharebite.app.ui.impact.ImpactFragment;
import com.sharebite.app.ui.listing.MyListingsFragment;
import com.sharebite.app.ui.profile.GuestAccountFragment;
import com.sharebite.app.ui.profile.ProfileFragment;
import com.sharebite.app.ui.reservation.ReservationsFragment;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.workers.PickupReminders;

import java.util.List;

/**
 * Single host for the five bottom-navigation tabs. The tab set depends on the role:
 * providers get Dashboard / My listings, receivers and charities get Home / Explore.
 * Fragments are kept alive with show/hide so each tab keeps its scroll position and filters.
 */
public class MainActivity extends BaseActivity {

    private static final String STATE_TAB = "selectedTab";

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private UserRole role;
    private boolean guest;
    @IdRes private int currentTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        User user = requireUser();
        if (user == null) return;
        role = user.getRole();
        guest = ServiceLocator.get().session().isGuest();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.fragmentContainer, true, false);
        floatNavAboveSystemBars();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        if (guest) viewModel.startGuest();
        else viewModel.start(user);

        binding.bottomNav.inflateMenu(guest ? R.menu.menu_nav_guest
                : role == UserRole.PROVIDER ? R.menu.menu_nav_provider
                : role == UserRole.CHARITY ? R.menu.menu_nav_charity : R.menu.menu_nav_receiver);
        currentTab = savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, R.id.nav_home) : R.id.nav_home;
        // Select before attaching the listener so the initial tab is shown exactly once.
        binding.bottomNav.setSelectedItemId(currentTab);
        binding.bottomNav.setOnItemSelectedListener(this::onTabSelected);
        binding.bottomNav.setOnItemReselectedListener(item -> { });
        showTab(currentTab);

        // Keep the profile live (e.g. charity verification approved while the app is open).
        ServiceLocator.get().session().currentUser().observe(this, updated -> {
            if (updated == null) {
                restartApp();
            } else if (updated.getRole() != role || ServiceLocator.get().session().isGuest() != guest) {
                recreate();
            } else if (!guest) {
                viewModel.start(updated);
            }
        });
        viewModel.reservations().observe(this, state -> {
            updateReservationBadge(state.data);
            syncPickupReminders(state.data);
        });
        viewModel.notifications().observe(this, this::onNotifications);
    }

    /**
     * The bottom navigation floats as a rounded pill: keep it clear of the gesture/navigation bar
     * by adding the system inset to its margins, and stop the insets reaching the nav view itself.
     */
    private void floatNavAboveSystemBars() {
        ViewGroup.MarginLayoutParams base = (ViewGroup.MarginLayoutParams) binding.navCard.getLayoutParams();
        final int start = base.leftMargin, end = base.rightMargin, bottom = base.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(binding.navCard, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.leftMargin = start + bars.left;
            lp.rightMargin = end + bars.right;
            lp.bottomMargin = bottom + bars.bottom;
            v.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    /** Lets fragments switch tabs (e.g. "See all" on the home screen opens Explore). */
    public void selectTab(@IdRes int tabId) {
        binding.bottomNav.setSelectedItemId(tabId);
    }

    private boolean onTabSelected(@NonNull MenuItem item) {
        showTab(item.getItemId());
        return true;
    }

    private void showTab(@IdRes int tabId) {
        currentTab = tabId;
        FragmentManager fm = getSupportFragmentManager();
        String tag = "tab_" + tabId;
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        for (Fragment f : fm.getFragments()) {
            if (f.getTag() != null && f.getTag().startsWith("tab_") && !tag.equals(f.getTag())) tx.hide(f);
        }
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            Fragment created = createTab(tabId);
            if (created != null) tx.add(R.id.fragmentContainer, created, tag);
        } else {
            tx.show(target);
        }
        tx.commit();
    }

    @Nullable
    private Fragment createTab(@IdRes int tabId) {
        if (tabId == R.id.nav_home) {
            if (role == UserRole.PROVIDER) return new ProviderDashboardFragment();
            if (role == UserRole.CHARITY) return new CharityHomeFragment();
            return new ReceiverHomeFragment();
        }
        if (tabId == R.id.nav_explore) return new ExploreFragment();
        if (tabId == R.id.nav_listings) return new MyListingsFragment();
        if (tabId == R.id.nav_reservations) return new ReservationsFragment();
        if (tabId == R.id.nav_impact) return new ImpactFragment();
        if (tabId == R.id.nav_profile) return guest ? new GuestAccountFragment() : new ProfileFragment();
        return null;
    }

    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> { });

    /** Receivers and charities get a local reminder before each confirmed pickup closes. */
    private void syncPickupReminders(@Nullable List<Reservation> list) {
        User me = ServiceLocator.get().session().user();
        if (list == null || guest || me == null || role == UserRole.PROVIDER) return;
        PickupReminders.sync(this, list, me.getUid());
        boolean hasConfirmed = false;
        for (Reservation r : list) if (r.getStatus() == ReservationStatus.CONFIRMED) hasConfirmed = true;
        askForNotificationsOnce(hasConfirmed);
    }

    /** Asks for the Android 13+ notification permission once, when a reminder first matters. */
    private void askForNotificationsOnce(boolean needed) {
        if (!needed || android.os.Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences prefs = getSharedPreferences("prompts", MODE_PRIVATE);
        if (prefs.getBoolean("asked_notifications", false)) return;
        prefs.edit().putBoolean("asked_notifications", true).apply();
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
    }

    /** Badge on the reservations tab: requests awaiting the provider, or the user's active ones. */
    private void updateReservationBadge(@Nullable List<Reservation> list) {
        int count = 0;
        if (list != null) {
            for (Reservation r : list) {
                if (role == UserRole.PROVIDER ? r.getStatus() == ReservationStatus.PENDING
                        : r.getStatus() == ReservationStatus.CONFIRMED) count++;
            }
        }
        if (count == 0) {
            binding.bottomNav.removeBadge(R.id.nav_reservations);
        } else {
            BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_reservations);
            badge.setNumber(count);
            badge.setBackgroundColor(getColor(R.color.forest_green));
            badge.setBadgeTextColor(getColor(R.color.white));
        }
    }

    private void onNotifications(List<AppNotification> list) {
        int unread = viewModel.unreadCount();
        if (unread == 0) {
            binding.bottomNav.removeBadge(R.id.nav_profile);
        } else {
            BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_profile);
            badge.setNumber(unread);
        }
    }
}
