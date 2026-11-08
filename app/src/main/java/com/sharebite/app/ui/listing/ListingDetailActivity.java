package com.sharebite.app.ui.listing;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharebite.app.R;
import com.sharebite.app.adapter.ReservationAdapter;
import com.sharebite.app.data.model.Charity;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.data.model.Provider;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.VerificationStatus;
import com.sharebite.app.data.remote.dto.WeatherInfo;
import com.sharebite.app.databinding.ActivityListingDetailBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.service.DistanceCalculator;
import com.sharebite.app.domain.service.FoodHandlingAdvisor;
import com.sharebite.app.domain.service.ReservationActions;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.ui.explore.MapSupport;
import com.sharebite.app.ui.reservation.ReservationActionController;
import com.sharebite.app.ui.reservation.ReservationDetailActivity;
import com.sharebite.app.ui.auth.LoginActivity;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.LocationHelper;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Full listing details. Consumers reserve from here; the owner manages the listing. */
public class ListingDetailActivity extends BaseActivity implements ReserveBottomSheet.Host {

    private static final String EXTRA_ID = "listingId";

    private ActivityListingDetailBinding binding;
    private ListingDetailViewModel viewModel;
    private User me;
    private ReservationAdapter ownerAdapter;
    private final ReservationActionController reservationActions = new ReservationActionController();
    private LocationHelper locationHelper;
    private double[] myLocation;

    public static Intent intent(Context context, String listingId) {
        return new Intent(context, ListingDetailActivity.class).putExtra(EXTRA_ID, listingId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        me = requireUser();
        if (me == null) return;
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        binding = ActivityListingDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);

        viewModel = new ViewModelProvider(this).get(ListingDetailViewModel.class);
        viewModel.start(id);

        ownerAdapter = new ReservationAdapter(me.getUid(), new ReservationAdapter.Listener() {
            @Override
            public void onOpen(@NonNull Reservation r) {
                startActivity(ReservationDetailActivity.intent(ListingDetailActivity.this, r.getReservationId()));
            }

            @Override
            public void onAction(@NonNull Reservation r, @NonNull ReservationActions.Action action) {
                reservationActions.request(ListingDetailActivity.this, r, action, me, new ReservationActionController.Listener() {
                    @Override public void onBusy(boolean busy) { ViewUtils.visible(binding.progress, busy); }

                    @Override
                    public void onResult(@NonNull String message, boolean success) {
                        showMessage(message);
                        viewModel.refreshReservations();
                    }
                });
            }
        });
        binding.reservationList.setLayoutManager(new LinearLayoutManager(this));
        binding.reservationList.setAdapter(ownerAdapter);
        binding.weatherRetry.setOnClickListener(v -> viewModel.retryWeather());

        viewModel.listing().observe(this, this::render);
        viewModel.provider().observe(this, this::renderProvider);
        viewModel.weather().observe(this, this::renderWeather);
        viewModel.listingReservations().observe(this, this::renderOwnerReservations);
        viewModel.busy().observe(this, busy -> {
            ViewUtils.visible(binding.progress, busy);
            binding.primaryButton.setEnabled(!busy);
        });
        viewModel.message().observe(this, e -> {
            String msg = e.consume();
            if (msg != null) showMessage(msg);
        });
        viewModel.reserved().observe(this, e -> {
            Reservation r = e.consume();
            if (r != null) showReservedDialog(r);
        });
        viewModel.closed().observe(this, e -> {
            if (e.consume() != null) finish();
        });

        if (LocationHelper.hasPermission(this)) {
            locationHelper = new LocationHelper(this);
            locationHelper.currentLocation(this, new ResultCallback<Location>() {
                @Override
                public void onSuccess(Location location) {
                    myLocation = new double[]{location.getLatitude(), location.getLongitude()};
                    FoodListing l = viewModel.current();
                    if (l != null) renderDistance(l);
                }

                @Override public void onError(@NonNull Exception error) { }
            });
        }
    }

    private void render(UiState<FoodListing> state) {
        ViewUtils.visible(binding.progress, state.isLoading());
        FoodListing l = state.data;
        if (l == null) {
            if (state.message != null) {
                binding.content.setVisibility(android.view.View.GONE);
                ViewUtils.visible(binding.actionBar, false);
                ViewUtils.visible(binding.errorState.getRoot(), true);
                binding.errorState.emptyTitle.setText(R.string.listing_unavailable_title);
                binding.errorState.emptyMessage.setText(state.message);
            }
            return;
        }
        binding.content.setVisibility(android.view.View.VISIBLE);
        Date now = new Date();
        ListingStatus status = l.effectiveStatus(now);

        ImageLoader.loadFood(binding.foodImage, l.getImageUrl());
        ViewUtils.styleDistributionBadge(binding.distributionBadge, l.getDistributionType());
        ViewUtils.styleListingStatus(binding.statusBadge, status);
        binding.categoryBadge.setText(l.getCategory() == null ? R.string.category_other : l.getCategory().labelRes);
        binding.title.setText(l.getTitle());
        binding.toolbar.setTitle(l.getTitle());
        binding.description.setText(l.getDescription());

        if (l.getDistributionType() == DistributionType.DISCOUNTED) {
            binding.price.setText(getString(R.string.price_each, Formatters.money(l.getDiscountedPrice())));
            binding.originalPrice.setText(Formatters.money(l.getOriginalPrice()));
            binding.originalPrice.setPaintFlags(binding.originalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            binding.discount.setText(getString(R.string.percent_off, l.discountPercent()));
            ViewUtils.visible(binding.originalPrice, true);
            ViewUtils.visible(binding.discount, l.discountPercent() > 0);
        } else {
            binding.price.setText(l.getDistributionType() == DistributionType.FREE ? R.string.price_free_long : R.string.price_charity_long);
            ViewUtils.visible(binding.originalPrice, false);
            ViewUtils.visible(binding.discount, false);
        }

        java.util.Set<com.sharebite.app.data.model.Allergen> mine = com.sharebite.app.domain.service.AllergenMatcher
                .conflicts(l.getAllergens(), ServiceLocator.get().allergens().avoided());
        ViewUtils.visible(binding.myAllergenAlert, !mine.isEmpty());
        if (!mine.isEmpty()) {
            binding.myAllergenTitle.setText(getString(R.string.allergen_conflict_title,
                    com.sharebite.app.utils.AllergenText.join(this, mine)));
        }

        boolean allergens = l.hasDeclaredAllergens();
        ViewUtils.visible(binding.allergenBanner, allergens);
        binding.allergenText.setText(l.getAllergens());

        binding.availableText.setText(getString(R.string.available_of_total,
                Formatters.quantity(this, l.getAvailableQuantity(), l.getUnit()), l.getTotalQuantity()));
        binding.stockProgress.setMax(Math.max(1, l.getTotalQuantity()));
        binding.stockProgress.setProgressCompat(l.getAvailableQuantity(), false);
        binding.pickupWindow.setText(getString(R.string.pickup_window,
                Formatters.dateTime(this, l.getPickupStartTime()), Formatters.dateTime(this, l.getPickupDeadline())));
        binding.timeLeft.setText(Formatters.timeLeft(this, l.getPickupDeadline(), now));

        binding.providerName.setText(l.getProviderName());
        binding.providerType.setText(l.getProviderType() == null ? R.string.role_provider : l.getProviderType().labelRes);

        binding.address.setText(l.getPickupAddress());
        binding.directionsButton.setOnClickListener(v -> {
            if (l.hasCoordinates()) MapSupport.openNavigation(this, l.getLatitude(), l.getLongitude(), l.getTitle());
            else MapSupport.openAddressSearch(this, l.getPickupAddress());
        });
        renderDistance(l);

        binding.preparedAt.setText(getString(R.string.prepared_at, Formatters.dateTime(this, l.getPreparationTime())));
        binding.storage.setText(getString(R.string.storage_line, l.getStorageInformation()));
        binding.allergensLine.setText(getString(R.string.allergens_line, l.getAllergens()));
        boolean hasNotes = l.getFoodSafetyNotes() != null && !l.getFoodSafetyNotes().trim().isEmpty();
        ViewUtils.visible(binding.safetyNotes, hasNotes);
        if (hasNotes) binding.safetyNotes.setText(l.getFoodSafetyNotes());

        ViewUtils.visible(binding.weatherCard, l.hasCoordinates());
        boolean owner = l.isOwnedBy(me.getUid());
        ViewUtils.visible(binding.ownerSection, owner);
        if (owner) bindOwnerActions(l, status);
        else bindConsumerActions(l, now);
    }

    private void renderDistance(FoodListing l) {
        boolean known = myLocation != null && l.hasCoordinates();
        ViewUtils.visible(binding.distance, known);
        if (known) {
            double km = DistanceCalculator.distanceKm(myLocation[0], myLocation[1], l.getLatitude(), l.getLongitude());
            binding.distance.setText(Formatters.distance(this, km));
        }
    }

    private void renderProvider(@Nullable User provider) {
        if (provider == null) return;
        binding.providerName.setText(provider.displayName());
        if (provider instanceof Provider) binding.providerType.setText(((Provider) provider).getProviderType().labelRes);
        String phone = provider.getPhone();
        boolean canCall = phone != null && !phone.isEmpty() && !provider.getUid().equals(me.getUid());
        ViewUtils.visible(binding.callButton, canCall);
        if (canCall) {
            binding.callButton.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))));
                } catch (android.content.ActivityNotFoundException e) {
                    showMessage(phone);
                }
            });
        }
    }

    private void renderWeather(@Nullable UiState<WeatherInfo> state) {
        if (state == null) return;
        ViewUtils.visible(binding.weatherProgress, state.isLoading());
        ViewUtils.visible(binding.weatherRetry, state.status == UiState.Status.ERROR);
        if (state.status == UiState.Status.ERROR) {
            binding.weatherSummary.setText(R.string.weather_unavailable);
            ViewUtils.visible(binding.weatherAdvice, false);
            return;
        }
        if (state.isLoading()) {
            binding.weatherSummary.setText(R.string.weather_loading);
            return;
        }
        WeatherInfo w = state.data;
        if (w == null) return;
        binding.weatherSummary.setText(getString(R.string.weather_summary,
                String.format(Locale.getDefault(), "%.0f°C", w.getTemperatureCelsius()), w.describe()));
        FoodListing l = viewModel.current();
        String advice = FoodHandlingAdvisor.advise(l == null ? null : l.getCategory(), w);
        ViewUtils.visible(binding.weatherAdvice, advice != null);
        binding.weatherAdvice.setText(advice);
    }

    private void bindConsumerActions(FoodListing l, Date now) {
        ViewUtils.visible(binding.actionBar, true);
        ViewUtils.visible(binding.secondaryButton, false);
        if (ServiceLocator.get().session().isGuest()) {
            // Browsing is open to everyone; acting needs an account.
            binding.primaryButton.setText(R.string.sign_in_to_reserve);
            binding.primaryButton.setEnabled(true);
            ViewUtils.visible(binding.actionNote, true);
            binding.actionNote.setText(R.string.guest_reserve_note);
            binding.primaryButton.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
            return;
        }
        binding.primaryButton.setText(R.string.reserve);
        String blocker = reserveBlocker(l, now);
        binding.primaryButton.setEnabled(blocker == null && !Boolean.TRUE.equals(viewModel.busy().getValue()));
        ViewUtils.visible(binding.actionNote, blocker != null);
        binding.actionNote.setText(blocker);
        binding.primaryButton.setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag(ReserveBottomSheet.TAG) == null) {
                ReserveBottomSheet.newInstance().show(getSupportFragmentManager(), ReserveBottomSheet.TAG);
            }
        });
    }

    /** Why the current user cannot reserve, or null when they can. */
    @Nullable
    private String reserveBlocker(FoodListing l, Date now) {
        ListingStatus status = l.effectiveStatus(now);
        if (status == ListingStatus.EXPIRED) return getString(R.string.blocker_expired);
        if (status != ListingStatus.AVAILABLE || l.getAvailableQuantity() <= 0) return getString(R.string.blocker_unavailable);
        if (!me.canReserve(l.getDistributionType())) {
            if (l.getDistributionType() == DistributionType.CHARITY && me instanceof Charity) {
                return me.getVerificationStatus() == VerificationStatus.PENDING
                        ? getString(R.string.blocker_verification_pending) : getString(R.string.blocker_verification_required);
            }
            if (l.getDistributionType() == DistributionType.CHARITY) return getString(R.string.blocker_charity_only);
            if (l.getDistributionType() == DistributionType.DISCOUNTED) return getString(R.string.blocker_discount_charity);
            return getString(R.string.blocker_role);
        }
        return null;
    }

    private void bindOwnerActions(FoodListing l, ListingStatus status) {
        ViewUtils.visible(binding.actionBar, true);
        ViewUtils.visible(binding.actionNote, false);
        boolean open = l.getStatus() != null && l.getStatus().isOpen();
        boolean closable = open || l.getStatus() == ListingStatus.EXPIRED;
        binding.primaryButton.setText(R.string.edit);
        binding.primaryButton.setEnabled(open && status != ListingStatus.EXPIRED);
        binding.primaryButton.setOnClickListener(v -> startActivity(ListingEditorActivity.editIntent(this, l.getListingId())));
        ViewUtils.visible(binding.secondaryButton, true);
        binding.secondaryButton.setText(R.string.manage);
        binding.secondaryButton.setOnClickListener(v -> showManageMenu(l, closable));
        if (status == ListingStatus.EXPIRED && open) {
            ViewUtils.visible(binding.actionNote, true);
            binding.actionNote.setText(R.string.owner_expired_note);
        }
    }

    private void showManageMenu(FoodListing l, boolean closable) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (closable) {
            labels.add(getString(R.string.mark_listing_completed));
            actions.add(() -> confirm(R.string.complete_title, R.string.complete_message, R.string.mark_listing_completed, viewModel::markCompleted));
            labels.add(getString(R.string.withdraw_listing));
            actions.add(() -> confirm(R.string.withdraw_title, R.string.withdraw_message, R.string.withdraw_listing, viewModel::withdraw));
        }
        labels.add(getString(R.string.delete_listing));
        actions.add(() -> confirm(R.string.delete_title, R.string.delete_message, R.string.delete_listing, viewModel::delete));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manage_listing)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void confirm(int title, int message, int action, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(action, (d, w) -> onConfirm.run())
                .show();
    }

    private void renderOwnerReservations(List<Reservation> list) {
        ownerAdapter.submitList(list);
        ViewUtils.visible(binding.reservationsEmpty, list == null || list.isEmpty());
    }

    private void showReservedDialog(Reservation r) {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_check_circle)
                .setTitle(R.string.reserved_title)
                .setMessage(getString(R.string.reserved_message, Formatters.quantity(this, r.getQuantity(), r.getUnit()),
                        r.getProviderName(), Formatters.dateTime(this, r.getPickupDeadline())))
                .setPositiveButton(R.string.view_reservation, (d, w) ->
                        startActivity(ReservationDetailActivity.intent(this, r.getReservationId())))
                .setNegativeButton(R.string.done, null)
                .show();
    }

    // ---- ReserveBottomSheet.Host -----------------------------------------------------------

    @Nullable
    @Override
    public FoodListing listingForReservation() {
        return viewModel.current();
    }

    @NonNull
    @Override
    public User reservingUser() {
        return me;
    }

    @Override
    public void onReserveConfirmed(int quantity) {
        viewModel.reserve(quantity);
    }

    @Override
    protected void onDestroy() {
        if (locationHelper != null) locationHelper.cancel();
        super.onDestroy();
    }
}
