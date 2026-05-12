package com.example.weconnect.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_INCOMING = 0;
    private static final int TYPE_OUTGOING = 1;

    public interface OnUserClickListener {
        void onUserClick(long userId, String userName, String avatarUrl);
    }

    private final Context context;
    private final List<ChatMessage> messages = new ArrayList<>();
    private OnUserClickListener userClickListener;

    public MessageAdapter(Context context) {
        this.context = context;
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.userClickListener = listener;
    }

    public void submitList(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isSentByCurrentUser() ? TYPE_OUTGOING : TYPE_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == TYPE_OUTGOING
                ? R.layout.item_message_outgoing
                : R.layout.item_message_incoming;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        MessageViewHolder messageHolder = (MessageViewHolder) holder;
        if (messageHolder.tvSender != null) messageHolder.tvSender.setText(message.getSenderName());
        messageHolder.tvContent.setText(message.getContent());
        messageHolder.tvTime.setText(message.getTimeLabel());

        if (messageHolder.ivMessageAvatar != null) {
            String avatarUrl = message.getSenderId() > 0
                    ? RetrofitClient.getCachedAvatarForUser(message.getSenderId()) : null;
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (avatarUrl.startsWith("/")) avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                com.bumptech.glide.Glide.with(context)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .circleCrop()
                        .into(messageHolder.ivMessageAvatar);
            } else {
                messageHolder.ivMessageAvatar.setImageResource(R.drawable.ic_user_placeholder);
            }
        }

        // Tap avatar/sender-name on incoming messages → user action sheet
        if (!message.isSentByCurrentUser() && userClickListener != null && message.getSenderId() > 0) {
            View.OnClickListener userClick = v -> {
                String av = RetrofitClient.getCachedAvatarForUser(message.getSenderId());
                userClickListener.onUserClick(message.getSenderId(), message.getSenderName(), av);
            };
            if (messageHolder.ivMessageAvatar != null)
                messageHolder.ivMessageAvatar.setOnClickListener(userClick);
            if (messageHolder.tvSender != null)
                messageHolder.tvSender.setOnClickListener(userClick);
        } else {
            if (messageHolder.ivMessageAvatar != null) messageHolder.ivMessageAvatar.setOnClickListener(null);
            if (messageHolder.tvSender != null) messageHolder.tvSender.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMessageAvatar;
        TextView tvSender;
        TextView tvContent;
        TextView tvTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMessageAvatar = itemView.findViewById(R.id.ivMessageAvatar);
            tvSender = itemView.findViewById(R.id.tvMessageSender);
            tvContent = itemView.findViewById(R.id.tvMessageContent);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
        }
    }
}
