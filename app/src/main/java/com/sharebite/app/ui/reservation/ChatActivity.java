package com.sharebite.app.ui.reservation;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sharebite.app.R;
import com.sharebite.app.adapter.ChatAdapter;
import com.sharebite.app.data.model.ChatMessage;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.ActivityChatBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.Event;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Messages between the provider and the collector of one reservation ("I'm at the gate").
 * Writing is open while the reservation is pending or confirmed; history stays readable after.
 */
public class ChatActivity extends BaseActivity {

    private static final String EXTRA_ID = "reservationId";

    public static Intent intent(Context context, String reservationId) {
        return new Intent(context, ChatActivity.class).putExtra(EXTRA_ID, reservationId);
    }

    /** Owns the two realtime listeners (messages and the reservation itself). */
    public static class ChatViewModel extends ViewModel {
        private final ServiceLocator services = ServiceLocator.get();
        private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
        private final MutableLiveData<Reservation> reservation = new MutableLiveData<>();
        private final MutableLiveData<Boolean> sending = new MutableLiveData<>(false);
        private final MutableLiveData<Event<String>> error = new MutableLiveData<>();
        private Subscription messagesSub = Subscription.NONE;
        private Subscription reservationSub = Subscription.NONE;
        private String id;

        void start(String reservationId) {
            if (reservationId.equals(id)) return;
            id = reservationId;
            reservationSub = services.reservations().observeById(reservationId, new ResultCallback<Reservation>() {
                @Override public void onSuccess(Reservation r) { reservation.setValue(r); }
                @Override public void onError(@NonNull Exception e) { error.setValue(new Event<>(ErrorMessages.from(e))); }
            });
            messagesSub = services.chat().observe(reservationId, new ResultCallback<List<ChatMessage>>() {
                @Override public void onSuccess(List<ChatMessage> result) { messages.setValue(result); }
                @Override public void onError(@NonNull Exception e) { error.setValue(new Event<>(ErrorMessages.from(e))); }
            });
        }

        void send(User me, String text) {
            if (Boolean.TRUE.equals(sending.getValue())) return;
            sending.setValue(true);
            services.chat().send(id, me, text, new ResultCallback<Void>() {
                @Override public void onSuccess(Void result) { sending.setValue(false); }

                @Override
                public void onError(@NonNull Exception e) {
                    sending.setValue(false);
                    error.setValue(new Event<>(ErrorMessages.from(e)));
                }
            });
        }

        LiveData<List<ChatMessage>> messages() { return messages; }
        LiveData<Reservation> reservation() { return reservation; }
        LiveData<Boolean> sending() { return sending; }
        LiveData<Event<String>> error() { return error; }

        @Override
        protected void onCleared() {
            messagesSub.cancel();
            reservationSub.cancel();
        }
    }

    private ActivityChatBinding binding;
    private ChatViewModel viewModel;
    private User me;

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
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);

        ChatAdapter adapter = new ChatAdapter(me.getUid());
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        binding.list.setLayoutManager(lm);
        binding.list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.start(id);
        viewModel.messages().observe(this, list -> {
            adapter.submitList(list, () -> {
                if (!list.isEmpty()) binding.list.scrollToPosition(list.size() - 1);
            });
            ViewUtils.visible(binding.emptyText, list.isEmpty());
        });
        viewModel.reservation().observe(this, this::renderReservation);
        viewModel.sending().observe(this, busy -> binding.sendButton.setEnabled(!busy));
        viewModel.error().observe(this, e -> {
            String msg = e.consume();
            if (msg != null) showMessage(msg);
        });

        binding.sendButton.setOnClickListener(v -> send());
        binding.input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                boolean typing = s.length() > 0;
                setTyping(typing);
                typingHandler.removeCallbacks(stopTyping);
                if (typing) typingHandler.postDelayed(stopTyping, 4000);
            }
        });
        binding.input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });
    }

    // ---- live presence and typing (Firebase Realtime Database) -----------------------------
    private com.sharebite.app.domain.interfaces.Subscription presenceSub = com.sharebite.app.domain.interfaces.Subscription.NONE;
    private com.sharebite.app.domain.interfaces.Subscription typingSub = com.sharebite.app.domain.interfaces.Subscription.NONE;
    private String otherUid, listingTitle, presenceText;
    private boolean otherTyping;
    private final android.os.Handler typingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable stopTyping = () -> setTyping(false);
    private boolean typingSent;

    private void watchCounterpart(String reservationId, String uid) {
        if (uid.equals(otherUid)) return;
        otherUid = uid;
        com.sharebite.app.domain.interfaces.PresenceRepository presence = ServiceLocator.get().presence();
        presenceSub.cancel();
        typingSub.cancel();
        presenceSub = presence.observe(uid, new ResultCallback<com.sharebite.app.domain.interfaces.PresenceRepository.Presence>() {
            @Override
            public void onSuccess(com.sharebite.app.domain.interfaces.PresenceRepository.Presence p) {
                presenceText = p.online ? getString(R.string.presence_online)
                        : p.lastChanged > 0 ? getString(R.string.presence_last_seen, android.text.format.DateUtils
                        .getRelativeTimeSpanString(p.lastChanged, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS))
                        : null;
                renderSubtitle();
            }

            @Override public void onError(@NonNull Exception e) { }
        });
        typingSub = presence.observeTyping(reservationId, uid, new ResultCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean typing) {
                otherTyping = Boolean.TRUE.equals(typing);
                renderSubtitle();
            }

            @Override public void onError(@NonNull Exception e) { }
        });
    }

    private void renderSubtitle() {
        if (otherTyping) binding.toolbar.setSubtitle(R.string.presence_typing);
        else if (presenceText != null) binding.toolbar.setSubtitle(presenceText + " \u00B7 " + listingTitle);
        else binding.toolbar.setSubtitle(listingTitle);
    }

    private void setTyping(boolean typing) {
        if (typing == typingSent || viewModelReservationId == null) return;
        typingSent = typing;
        ServiceLocator.get().presence().setTyping(viewModelReservationId, me.getUid(), typing);
    }

    private String viewModelReservationId;

    @Override
    protected void onDestroy() {
        typingHandler.removeCallbacks(stopTyping);
        if (me != null) setTyping(false);
        presenceSub.cancel();
        typingSub.cancel();
        super.onDestroy();
    }

    private void renderReservation(Reservation r) {
        if (r == null) return;
        boolean iAmProvider = me.getUid().equals(r.getProviderId());
        binding.toolbar.setTitle(iAmProvider ? r.getReceiverName() : r.getProviderName());
        listingTitle = r.getListingTitle();
        viewModelReservationId = r.getReservationId();
        renderSubtitle();
        watchCounterpart(r.getReservationId(), iAmProvider ? r.getReceiverId() : r.getProviderId());
        boolean open = r.getStatus() == ReservationStatus.PENDING || r.getStatus() == ReservationStatus.CONFIRMED;
        ViewUtils.visible(binding.composer, open);
        ViewUtils.visible(binding.closedText, !open);
    }

    private void send() {
        String text = binding.input.getText() == null ? "" : binding.input.getText().toString().trim();
        if (text.isEmpty()) return;
        if (text.length() > ChatMessage.MAX_LENGTH) {
            binding.input.setError(getString(R.string.chat_too_long, ChatMessage.MAX_LENGTH));
            return;
        }
        binding.input.setText("");
        viewModel.send(me, text);
    }
}
