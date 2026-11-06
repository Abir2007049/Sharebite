package com.sharebite.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.sharebite.app.R;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.ViewUtils;

import java.util.Date;
import java.util.Objects;

/** Provider's own listings with stock progress and status. */
public class MyListingAdapter extends ListAdapter<FoodListing, MyListingAdapter.Holder> {

    public interface OnClick {
        void onClick(@NonNull FoodListing listing);
    }

    private final OnClick listener;

    public MyListingAdapter(@NonNull OnClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_listing, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        FoodListing l = getItem(position);
        Context c = h.itemView.getContext();
        Date now = new Date();
        ListingStatus status = l.effectiveStatus(now);

        ImageLoader.loadFood(h.image, l.getImageUrl());
        h.title.setText(l.getTitle());
        ViewUtils.styleListingStatus(h.status, status);
        ViewUtils.styleDistributionBadge(h.distribution, l.getDistributionType());
        h.stock.setText(c.getString(R.string.stock_format,
                l.reservedQuantity(), Formatters.quantity(c, l.getTotalQuantity(), l.getUnit())));
        h.progress.setMax(Math.max(1, l.getTotalQuantity()));
        h.progress.setProgressCompat(l.reservedQuantity(), false);
        h.deadline.setText(status.isOpen()
                ? c.getString(R.string.pickup_by, Formatters.dateTime(c, l.getPickupDeadline()))
                : c.getString(R.string.deadline_was, Formatters.dateTime(c, l.getPickupDeadline())));
        h.itemView.setOnClickListener(v -> listener.onClick(l));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, status, distribution, stock, deadline;
        final LinearProgressIndicator progress;

        Holder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.foodImage);
            title = v.findViewById(R.id.foodTitle);
            status = v.findViewById(R.id.statusBadge);
            distribution = v.findViewById(R.id.distributionBadge);
            stock = v.findViewById(R.id.stock);
            deadline = v.findViewById(R.id.deadline);
            progress = v.findViewById(R.id.stockProgress);
        }
    }

    private static final DiffUtil.ItemCallback<FoodListing> DIFF = new DiffUtil.ItemCallback<FoodListing>() {
        @Override
        public boolean areItemsTheSame(@NonNull FoodListing a, @NonNull FoodListing b) {
            return a.getListingId().equals(b.getListingId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull FoodListing a, @NonNull FoodListing b) {
            return a.getAvailableQuantity() == b.getAvailableQuantity() && a.getTotalQuantity() == b.getTotalQuantity()
                    && a.getStatus() == b.getStatus() && Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getImageUrl(), b.getImageUrl())
                    && Objects.equals(a.getPickupDeadline(), b.getPickupDeadline());
        }
    };
}
