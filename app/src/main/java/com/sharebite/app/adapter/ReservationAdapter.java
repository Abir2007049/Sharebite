package com.sharebite.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.sharebite.app.R;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.domain.service.ReservationActions;
import com.sharebite.app.domain.service.ReservationActions.Action;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.ViewUtils;

import java.util.Objects;

/** Reservation rows with role-appropriate action buttons. */
public class ReservationAdapter extends ListAdapter<Reservation, ReservationAdapter.Holder> {

    public interface Listener {
        void onOpen(@NonNull Reservation reservation);

        void onAction(@NonNull Reservation reservation, @NonNull Action action);
    }

    private final String viewerUid;
    private final Listener listener;

    public ReservationAdapter(@NonNull String viewerUid, @NonNull Listener listener) {
        super(DIFF);
        this.viewerUid = viewerUid;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reservation, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Reservation r = getItem(position);
        Context c = h.itemView.getContext();
        boolean viewerIsProvider = viewerUid.equals(r.getProviderId());

        ImageLoader.loadFood(h.image, r.getListingImageUrl());
        h.title.setText(r.getListingTitle());
        if (viewerIsProvider) {
            int roleLabel = r.getReceiverRole() == UserRole.CHARITY ? R.string.counterpart_charity : R.string.counterpart_receiver;
            h.counterpart.setText(c.getString(roleLabel, r.getReceiverName()));
        } else {
            h.counterpart.setText(c.getString(R.string.counterpart_provider, r.getProviderName()));
        }
        String qty = Formatters.quantity(c, r.getQuantity(), r.getUnit());
        if (r.getDistributionType() == DistributionType.DISCOUNTED) {
            qty = c.getString(R.string.quantity_price_format, qty, Formatters.money(r.getTotalPrice()));
        }
        h.quantity.setText(qty);
        ViewUtils.styleReservationStatus(h.status, r.getStatus() == null ? ReservationStatus.PENDING : r.getStatus());
        h.time.setText(timeLine(c, r));

        ReservationActions.Available actions = ReservationActions.forViewer(r, viewerUid);
        ViewUtils.visible(h.actions, !actions.isEmpty());
        bindButton(h.primary, actions.primary, r, viewerIsProvider);
        bindButton(h.secondary, actions.secondary, r, viewerIsProvider);
        h.itemView.setOnClickListener(v -> listener.onOpen(r));
    }

    private String timeLine(Context c, Reservation r) {
        ReservationStatus s = r.getStatus();
        if (s == ReservationStatus.COLLECTED) return c.getString(R.string.collected_on, Formatters.dateTime(c, r.getCollectedAt()));
        if (s == ReservationStatus.CANCELLED) return c.getString(R.string.cancelled_on, Formatters.dateTime(c, r.getCancelledAt()));
        if (s == ReservationStatus.REJECTED) return c.getString(R.string.declined_on, Formatters.dateTime(c, r.getRejectedAt()));
        return c.getString(R.string.pickup_by, Formatters.dateTime(c, r.getPickupDeadline()));
    }

    private void bindButton(MaterialButton button, @Nullable Action action, Reservation r, boolean viewerIsProvider) {
        if (action == null) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setText(labelFor(action, viewerIsProvider));
        button.setOnClickListener(v -> listener.onAction(r, action));
    }

    @StringRes
    public static int labelFor(@NonNull Action action, boolean viewerIsProvider) {
        switch (action) {
            case CONFIRM: return R.string.action_confirm;
            case REJECT: return R.string.action_decline;
            case COLLECT: return viewerIsProvider ? R.string.action_mark_collected : R.string.action_confirm_pickup;
            case CANCEL: default: return R.string.action_cancel_reservation;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, counterpart, quantity, status, time;
        final View actions;
        final MaterialButton primary, secondary;

        Holder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.foodImage);
            title = v.findViewById(R.id.foodTitle);
            counterpart = v.findViewById(R.id.counterpart);
            quantity = v.findViewById(R.id.quantity);
            status = v.findViewById(R.id.statusBadge);
            time = v.findViewById(R.id.timeLine);
            actions = v.findViewById(R.id.actions);
            primary = v.findViewById(R.id.primaryAction);
            secondary = v.findViewById(R.id.secondaryAction);
        }
    }

    private static final DiffUtil.ItemCallback<Reservation> DIFF = new DiffUtil.ItemCallback<Reservation>() {
        @Override
        public boolean areItemsTheSame(@NonNull Reservation a, @NonNull Reservation b) {
            return a.getReservationId().equals(b.getReservationId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Reservation a, @NonNull Reservation b) {
            return Objects.equals(a.getStatus(), b.getStatus()) && a.getQuantity() == b.getQuantity()
                    && Objects.equals(a.getListingImageUrl(), b.getListingImageUrl());
        }
    };
}
