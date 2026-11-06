package com.sharebite.app.ui.profile;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sharebite.app.R;
import com.sharebite.app.adapter.NotificationAdapter;
import com.sharebite.app.data.model.AppNotification;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.ActivityNotificationsBinding;
import com.sharebite.app.domain.interfaces.NotificationRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.ui.listing.ListingDetailActivity;
import com.sharebite.app.ui.reservation.ReservationDetailActivity;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

import java.util.List;

/** The user's in-app notifications (reservation requests, confirmations, collections...). */
public class NotificationsActivity extends BaseActivity {

    /** Realtime list of the recipient's notifications. */
    public static class NotificationsViewModel extends ViewModel {
        private final NotificationRepository repo = ServiceLocator.get().notifications();
        private final MutableLiveData<UiState<List<AppNotification>>> items = new MutableLiveData<>(UiState.loading(null));
        private Subscription subscription = Subscription.NONE;
        private boolean started;

        void start(String uid) {
            if (started) return;
            started = true;
            subscription = repo.observeForRecipient(uid, new ResultCallback<List<AppNotification>>() {
                @Override public void onSuccess(List<AppNotification> result) { items.setValue(UiState.success(result)); }
                @Override public void onError(@NonNull Exception e) { items.setValue(UiState.error(ErrorMessages.from(e), null)); }
            });
        }

        LiveData<UiState<List<AppNotification>>> items() { return items; }

        void markRead(AppNotification n) {
            if (!n.isRead()) repo.markRead(n.getNotificationId(), ignore());
        }

        void markAllRead() {
            UiState<List<AppNotification>> s = items.getValue();
            if (s != null && s.data != null) repo.markAllRead(s.data, ignore());
        }

        private static ResultCallback<Void> ignore() {
            return new ResultCallback<Void>() {
                @Override public void onSuccess(Void result) { }
                @Override public void onError(@NonNull Exception error) { }
            };
        }

        @Override
        protected void onCleared() {
            subscription.cancel();
        }
    }

    private static final int MENU_MARK_ALL = 1;

    private ActivityNotificationsBinding binding;
    private NotificationsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        User user = requireUser();
        if (user == null) return;
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);

        viewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);
        viewModel.start(user.getUid());

        NotificationAdapter adapter = new NotificationAdapter(n -> {
            viewModel.markRead(n);
            if (n.getRelatedReservationId() != null) {
                startActivity(ReservationDetailActivity.intent(this, n.getRelatedReservationId()));
            } else if (n.getRelatedListingId() != null) {
                startActivity(ListingDetailActivity.intent(this, n.getRelatedListingId()));
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        binding.list.setAdapter(adapter);
        binding.emptyState.emptyTitle.setText(R.string.empty_notifications_title);
        binding.emptyState.emptyMessage.setText(R.string.empty_notifications_message);
        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_notifications);

        MenuItem markAll = binding.toolbar.getMenu().add(0, MENU_MARK_ALL, 0, R.string.mark_all_read);
        markAll.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        markAll.setVisible(false);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() != MENU_MARK_ALL) return false;
            viewModel.markAllRead();
            return true;
        });

        viewModel.items().observe(this, state -> {
            ViewUtils.visible(binding.progress, state.isLoading());
            if (state.message != null) showMessage(state.message);
            List<AppNotification> list = state.data;
            adapter.submitList(list);
            ViewUtils.visible(binding.emptyState.getRoot(), list != null && list.isEmpty());
            boolean anyUnread = false;
            if (list != null) for (AppNotification n : list) if (!n.isRead()) { anyUnread = true; break; }
            markAll.setVisible(anyUnread);
        });
    }
}
