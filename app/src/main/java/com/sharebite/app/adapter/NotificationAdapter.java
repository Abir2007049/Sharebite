package com.sharebite.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.sharebite.app.R;
import com.sharebite.app.data.model.AppNotification;
import com.sharebite.app.data.model.NotificationType;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ViewUtils;

/** In-app notification rows; unread items are highlighted. */
public class NotificationAdapter extends ListAdapter<AppNotification, NotificationAdapter.Holder> {

    public interface OnClick {
        void onClick(@NonNull AppNotification notification);
    }

    private final OnClick listener;

    public NotificationAdapter(@NonNull OnClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppNotification n = getItem(position);
        h.title.setText(n.getTitle());
        h.message.setText(n.getMessage());
        h.time.setText(Formatters.relative(n.getCreatedAt()));
        h.icon.setImageResource(iconFor(n.getType()));
        ViewUtils.visible(h.unreadDot, !n.isRead());
        h.itemView.setBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), n.isRead() ? R.color.white : R.color.mint_container));
        h.itemView.setOnClickListener(v -> listener.onClick(n));
    }

    @DrawableRes
    private static int iconFor(NotificationType type) {
        if (type == null) return R.drawable.ic_notifications;
        switch (type) {
            case RESERVATION_REQUEST: return R.drawable.ic_assignment;
            case RESERVATION_CONFIRMED: return R.drawable.ic_check_circle;
            case COLLECTION_COMPLETED: return R.drawable.ic_eco;
            case RESERVATION_REJECTED:
            case RESERVATION_CANCELLED:
            case LISTING_CANCELLED: return R.drawable.ic_close;
            default: return R.drawable.ic_notifications;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title, message, time;
        final View unreadDot;

        Holder(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            message = v.findViewById(R.id.message);
            time = v.findViewById(R.id.time);
            unreadDot = v.findViewById(R.id.unreadDot);
        }
    }

    private static final DiffUtil.ItemCallback<AppNotification> DIFF = new DiffUtil.ItemCallback<AppNotification>() {
        @Override
        public boolean areItemsTheSame(@NonNull AppNotification a, @NonNull AppNotification b) {
            return a.getNotificationId().equals(b.getNotificationId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull AppNotification a, @NonNull AppNotification b) {
            return a.isRead() == b.isRead() && a.getCreatedAt() != null && a.getCreatedAt().equals(b.getCreatedAt());
        }
    };
}
