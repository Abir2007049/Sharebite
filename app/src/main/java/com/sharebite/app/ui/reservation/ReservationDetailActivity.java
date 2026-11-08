package com.sharebite.app.ui.reservation;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.Timestamp;
import com.sharebite.app.R;
import com.sharebite.app.adapter.ReservationAdapter;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.ActivityReservationDetailBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;
import com.sharebite.app.domain.service.ReservationActions;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.ui.explore.MapSupport;
import com.sharebite.app.ui.listing.ListingDetailActivity;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

/** One reservation: status timeline, pickup information and the actions available to the viewer. */
public class ReservationDetailActivity extends BaseActivity {

    private static final String EXTRA_ID = "reservationId";

    /** Realtime reservation document so status changes by the other party appear immediately. */
    public static class DetailViewModel extends ViewModel {
        private final MutableLiveData<UiState<Reservation>> reservation = new MutableLiveData<>(UiState.loading(null));
        private Subscription subscription = Subscription.NONE;
        private String id;

        void start(String reservationId) {
            if (reservationId.equals(id)) return;
            id = reservationId;
            subscription = ServiceLocator.get().reservations().observeById(reservationId, new ResultCallback<Reservation>() {
                @Override
                public void onSuccess(Reservation result) {
                    reservation.setValue(result == null ? UiState.error("Reservation not found.", null) : UiState.success(result));
                }

                @Override
                public void onError(@NonNull Exception e) {
                    reservation.setValue(UiState.error(ErrorMessages.from(e), null));
                }
            });
        }

        LiveData<UiState<Reservation>> reservation() { return reservation; }

        @Override
        protected void onCleared() {
            subscription.cancel();
        }
    }

    private ActivityReservationDetailBinding binding;
    private final ReservationActionController actions = new ReservationActionController();
    private User me;

    public static Intent intent(Context context, String reservationId) {
        return new Intent(context, ReservationDetailActivity.class).putExtra(EXTRA_ID, reservationId);
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
        binding = ActivityReservationDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);

        DetailViewModel vm = new ViewModelProvider(this).get(DetailViewModel.class);
        vm.start(id);
        vm.reservation().observe(this, this::render);
    }

    private void render(UiState<Reservation> state) {
        ViewUtils.visible(binding.progress, state.isLoading());
        Reservation r = state.data;
        if (r == null) {
            if (state.message != null) {
                showMessage(state.message);
                binding.content.setVisibility(View.GONE);
            }
            return;
        }
        binding.content.setVisibility(View.VISIBLE);
        boolean viewerIsProvider = me.getUid().equals(r.getProviderId());
        ReservationStatus status = r.getStatus() == null ? ReservationStatus.PENDING : r.getStatus();

        ImageLoader.loadFood(binding.foodImage, r.getListingImageUrl());
        binding.title.setText(r.getListingTitle());
        ViewUtils.styleReservationStatus(binding.statusBadge, status);
        if (r.getDistributionType() != null) ViewUtils.styleDistributionBadge(binding.distributionBadge, r.getDistributionType());
        binding.quantity.setText(Formatters.quantity(this, r.getQuantity(), r.getUnit()));
        binding.total.setText(r.getDistributionType() == DistributionType.DISCOUNTED
                ? getString(R.string.total_pay_at_pickup, Formatters.money(r.getTotalPrice()))
                : getString(r.getDistributionType() == DistributionType.CHARITY ? R.string.total_charity : R.string.total_free));
        binding.statusExplanation.setText(explanation(status, viewerIsProvider));

        binding.counterpartLabel.setText(viewerIsProvider ? R.string.collector : R.string.provider);
        binding.counterpartName.setText(viewerIsProvider ? r.getReceiverName() : r.getProviderName());
        String phone = viewerIsProvider ? r.getReceiverPhone() : null;
        ViewUtils.visible(binding.callButton, phone != null && !phone.isEmpty());
        binding.callButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))));
            } catch (android.content.ActivityNotFoundException e) {
                showMessage(phone);
            }
        });

        binding.address.setText(r.getPickupAddress());
        binding.window.setText(getString(R.string.pickup_window,
                Formatters.dateTime(this, r.getPickupStartTime()), Formatters.dateTime(this, r.getPickupDeadline())));
        binding.directionsButton.setOnClickListener(v -> MapSupport.openAddressSearch(this, r.getPickupAddress()));
        binding.viewListingButton.setOnClickListener(v -> startActivity(ListingDetailActivity.intent(this, r.getListingId())));

        renderTimeline(r);
        renderActions(r, viewerIsProvider);
        renderHandover(r, viewerIsProvider, status);
    }

    // ---- QR hand-over ---------------------------------------------------------------------

    private Reservation current;
    private final androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> scanner =
            registerForActivityResult(new com.journeyapps.barcodescanner.ScanContract(), result -> onScanned(result.getContents()));

    private void renderHandover(Reservation r, boolean viewerIsProvider, ReservationStatus status) {
        current = r;
        boolean active = status == ReservationStatus.CONFIRMED;
        ViewUtils.visible(binding.handoverCard, active || r.isPickupVerified());
        if (!active) {
            binding.handoverText.setText(R.string.handover_verified);
            ViewUtils.visible(binding.qrButton, false);
            return;
        }
        binding.handoverText.setText(viewerIsProvider ? R.string.handover_text_provider : R.string.handover_text_receiver);
        ViewUtils.visible(binding.qrButton, true);
        binding.qrButton.setText(viewerIsProvider ? R.string.scan_pickup_qr : R.string.show_pickup_qr);
        binding.qrButton.setOnClickListener(v -> {
            if (viewerIsProvider) startScan();
            else showQr(r);
        });
    }

    private void showQr(Reservation r) {
        ViewUtils.visible(binding.progress, true);
        ServiceLocator.get().reservations().getPickupCode(r.getReservationId(), new ResultCallback<String>() {
            @Override
            public void onSuccess(String code) {
                ViewUtils.visible(binding.progress, false);
                if (code == null) {
                    showMessage(getString(R.string.qr_unavailable));
                    return;
                }
                int size = (int) (getResources().getDisplayMetrics().density * 240);
                android.graphics.Bitmap bmp = com.sharebite.app.utils.QrCodes.render(
                        com.sharebite.app.domain.service.PickupCode.payload(r.getReservationId(), code),
                        size, ContextCompat.getColor(ReservationDetailActivity.this, R.color.charcoal));
                if (bmp == null) {
                    showMessage(getString(R.string.qr_unavailable));
                    return;
                }
                ImageView image = new ImageView(ReservationDetailActivity.this);
                image.setImageBitmap(bmp);
                image.setContentDescription(getString(R.string.pickup_qr_description));
                int pad = (int) (getResources().getDisplayMetrics().density * 16);
                image.setPadding(pad, pad, pad, 0);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(ReservationDetailActivity.this)
                        .setTitle(R.string.pickup_qr_title)
                        .setMessage(R.string.pickup_qr_message)
                        .setView(image)
                        .setPositiveButton(R.string.done, null)
                        .show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                ViewUtils.visible(binding.progress, false);
                showMessage(com.sharebite.app.utils.ErrorMessages.from(error));
            }
        });
    }

    private void startScan() {
        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions()
                .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false);
        scanner.launch(options);
    }

    private void onScanned(@Nullable String contents) {
        if (contents == null || current == null) return; // cancelled
        com.sharebite.app.domain.service.PickupCode.Scanned scanned = com.sharebite.app.domain.service.PickupCode.parse(contents);
        if (scanned == null) {
            showMessage(getString(R.string.scan_not_sharebite));
            return;
        }
        if (!scanned.reservationId.equals(current.getReservationId())) {
            showMessage(getString(R.string.scan_other_reservation));
            return;
        }
        ViewUtils.visible(binding.progress, true);
        binding.qrButton.setEnabled(false);
        ServiceLocator.get().reservations().markCollectedWithCode(current, me, scanned.code, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                ViewUtils.visible(binding.progress, false);
                binding.qrButton.setEnabled(true);
                showMessage(getString(R.string.scan_verified));
            }

            @Override
            public void onError(@NonNull Exception error) {
                ViewUtils.visible(binding.progress, false);
                binding.qrButton.setEnabled(true);
                boolean denied = error instanceof com.google.firebase.firestore.FirebaseFirestoreException
                        && ((com.google.firebase.firestore.FirebaseFirestoreException) error).getCode()
                        == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED;
                showMessage(denied ? getString(R.string.scan_mismatch) : com.sharebite.app.utils.ErrorMessages.from(error));
            }
        });
    }

    @StringRes
    private int explanation(ReservationStatus status, boolean provider) {
        switch (status) {
            case PENDING: return provider ? R.string.explain_pending_provider : R.string.explain_pending_consumer;
            case CONFIRMED: return provider ? R.string.explain_confirmed_provider : R.string.explain_confirmed_consumer;
            case COLLECTED: return R.string.explain_collected;
            case REJECTED: return R.string.explain_rejected;
            case CANCELLED: default: return R.string.explain_cancelled;
        }
    }

    private void renderTimeline(Reservation r) {
        binding.timeline.removeAllViews();
        addStep(R.string.timeline_requested, r.getReservedAt(), true);
        if (r.getStatus() == ReservationStatus.REJECTED) {
            addStep(R.string.timeline_declined, r.getRejectedAt(), true);
            return;
        }
        addStep(R.string.timeline_confirmed, r.getConfirmedAt(), r.getConfirmedAt() != null);
        if (r.getStatus() == ReservationStatus.CANCELLED) {
            addStep(R.string.timeline_cancelled, r.getCancelledAt(), true);
            return;
        }
        addStep(r.isPickupVerified() ? R.string.timeline_collected_qr
                : r.getCollectionConfirmedBy() == Reservation.CollectionConfirmer.RECEIVER
                ? R.string.timeline_collected_receiver : R.string.timeline_collected, r.getCollectedAt(), r.getCollectedAt() != null);
    }

    private void addStep(@StringRes int label, Timestamp at, boolean done) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_timeline_step, binding.timeline, false);
        ImageView dot = row.findViewById(R.id.stepIcon);
        TextView title = row.findViewById(R.id.stepTitle);
        TextView time = row.findViewById(R.id.stepTime);
        title.setText(label);
        title.setTextColor(ContextCompat.getColor(this, done ? R.color.charcoal : R.color.charcoal_hint));
        dot.setImageResource(done ? R.drawable.ic_check_circle : R.drawable.ic_schedule);
        dot.setColorFilter(ContextCompat.getColor(this, done ? R.color.leaf_green : R.color.charcoal_hint));
        time.setText(done && at != null ? Formatters.dateTime(this, at) : getString(R.string.timeline_waiting));
        binding.timeline.addView(row);
    }

    private void renderActions(Reservation r, boolean viewerIsProvider) {
        ReservationActions.Available available = ReservationActions.forViewer(r, me.getUid());
        ViewUtils.visible(binding.actionBar, !available.isEmpty());
        bind(binding.primaryButton, available.primary, r, viewerIsProvider);
        bind(binding.secondaryButton, available.secondary, r, viewerIsProvider);
    }

    private void bind(com.google.android.material.button.MaterialButton button, ReservationActions.Action action,
                      Reservation r, boolean viewerIsProvider) {
        ViewUtils.visible(button, action != null);
        if (action == null) return;
        button.setText(ReservationAdapter.labelFor(action, viewerIsProvider));
        button.setOnClickListener(v -> actions.request(this, r, action, me, new ReservationActionController.Listener() {
            @Override
            public void onBusy(boolean busy) {
                ViewUtils.visible(binding.progress, busy);
                binding.primaryButton.setEnabled(!busy);
                binding.secondaryButton.setEnabled(!busy);
            }

            @Override
            public void onResult(@NonNull String message, boolean success) {
                showMessage(message);
            }
        }));
    }
}
