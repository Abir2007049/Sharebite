package com.sharebite.app.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.sharebite.app.R;
import com.sharebite.app.data.model.ChatMessage;
import com.sharebite.app.utils.Formatters;

import java.util.Objects;

/** Chat bubbles: own messages on the right in brand green, the other person's on the left in white. */
public class ChatAdapter extends ListAdapter<ChatMessage, ChatAdapter.Holder> {

    private final String myUid;

    public ChatAdapter(@NonNull String myUid) {
        super(DIFF);
        this.myUid = myUid;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ChatMessage m = getItem(position);
        boolean mine = m.isFrom(myUid);
        h.text.setText(m.getText());
        h.time.setText(m.getCreatedAt() == null ? "" : Formatters.dateTime(h.itemView.getContext(), m.getCreatedAt()));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.bubble.getLayoutParams();
        lp.gravity = mine ? Gravity.END : Gravity.START;
        h.bubble.setLayoutParams(lp);
        h.bubble.setBackgroundResource(mine ? R.drawable.bg_bubble_mine : R.drawable.bg_bubble_theirs);
        int fg = ContextCompat.getColor(h.itemView.getContext(), mine ? R.color.white : R.color.charcoal);
        h.text.setTextColor(fg);
        h.time.setTextColor(ContextCompat.getColor(h.itemView.getContext(), mine ? R.color.soft_green : R.color.charcoal_hint));
        h.time.setGravity(mine ? Gravity.END : Gravity.START);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout bubble;
        final TextView text, time;

        Holder(@NonNull View v) {
            super(v);
            bubble = v.findViewById(R.id.bubble);
            text = v.findViewById(R.id.messageText);
            time = v.findViewById(R.id.messageTime);
        }
    }

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF = new DiffUtil.ItemCallback<ChatMessage>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
            return Objects.equals(a.getMessageId(), b.getMessageId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
            return Objects.equals(a.getText(), b.getText()) && Objects.equals(a.getCreatedAt(), b.getCreatedAt());
        }
    };
}
