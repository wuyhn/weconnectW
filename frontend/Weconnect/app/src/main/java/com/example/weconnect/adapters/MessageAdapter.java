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
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_INCOMING = 0;
    private static final int TYPE_OUTGOING = 1;
    private static final int TYPE_FRIEND_CARD = 2;
    private static final int TYPE_SYSTEM = 3;
    private static final int TYPE_SUMMARY = 4;

    public interface OnUserClickListener {
        void onUserClick(long userId, String userName, String avatarUrl);
    }

    public interface FriendCardListener {
        void onAddFriend();
        void onRespondToRequest();
        void onViewProfile();
    }

    private final Context context;
    private final List<ChatMessage> messages = new ArrayList<>();
    private OnUserClickListener userClickListener;
    private long maskedUserId = -1; // ID của user đã chặn mình (direct room) — ẩn tên và avatar

    // Group chat block: ẩn nội dung tin nhắn theo chiều block
    private final Set<Long> groupBlockedByMeIds = new HashSet<>();  // tôi chặn sender
    private final Set<Long> groupHasBlockedMeIds = new HashSet<>(); // sender chặn tôi

    // Tin nhắn bị ẩn mà user đã bấm "Nhấn để xem" — local session only
    private final Set<ChatMessage> expandedMessages = new HashSet<>();

    // Friend card state (position 0 khi visible, cuộn theo message list)
    private boolean showFriendCard = false;
    private String friendStatus = "NONE"; // NONE, PENDING_SENT, PENDING_RECEIVED, FRIEND
    private String friendCardName = "";
    private String friendCardAvatarUrl = "";
    private FriendCardListener friendCardListener;

    public MessageAdapter(Context context) {
        this.context = context;
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.userClickListener = listener;
    }

    public void setMaskedUserId(long userId) {
        this.maskedUserId = userId;
        notifyDataSetChanged();
    }

    public void setGroupBlockedSets(Set<Long> blockedByMe, Set<Long> hasBlockedMe) {
        groupBlockedByMeIds.clear();
        if (blockedByMe != null) groupBlockedByMeIds.addAll(blockedByMe);
        groupHasBlockedMeIds.clear();
        if (hasBlockedMe != null) groupHasBlockedMeIds.addAll(hasBlockedMe);
        notifyDataSetChanged();
    }

    public void setFriendCardListener(FriendCardListener listener) {
        this.friendCardListener = listener;
    }

    public void setFriendCard(boolean show, String status, String name, String avatarUrl) {
        this.showFriendCard = show;
        this.friendStatus = status != null ? status : "NONE";
        this.friendCardName = name != null ? name : "";
        this.friendCardAvatarUrl = avatarUrl != null ? avatarUrl : "";
        notifyDataSetChanged();
    }

    public void submitList(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void appendMessage(ChatMessage msg) {
        messages.add(msg);
        int adapterPos = messages.size() - 1 + (showFriendCard ? 1 : 0);
        notifyItemInserted(adapterPos);
    }

    public int countMessagesBySender(long senderId) {
        int count = 0;
        for (ChatMessage msg : messages) {
            if (!msg.isSystemMessage() && msg.getSenderId() == senderId) count++;
        }
        return count;
    }

    @Override
    public int getItemCount() {
        return messages.size() + (showFriendCard ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (showFriendCard && position == 0) return TYPE_FRIEND_CARD;
        int msgPos = showFriendCard ? position - 1 : position;
        ChatMessage msg = messages.get(msgPos);
        if (msg.isSummaryMessage()) return TYPE_SUMMARY;
        if (msg.isSystemMessage()) return TYPE_SYSTEM;
        return msg.isSentByCurrentUser() ? TYPE_OUTGOING : TYPE_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_FRIEND_CARD) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend_card, parent, false);
            return new FriendCardViewHolder(view);
        }
        if (viewType == TYPE_SYSTEM) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_system, parent, false);
            return new SystemMessageViewHolder(view);
        }
        if (viewType == TYPE_SUMMARY) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_summary, parent, false);
            return new SummaryMessageViewHolder(view);
        }
        int layoutId = viewType == TYPE_OUTGOING
                ? R.layout.item_message_outgoing
                : R.layout.item_message_incoming;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FriendCardViewHolder) {
            bindFriendCard((FriendCardViewHolder) holder);
            return;
        }
        int msgPos = showFriendCard ? position - 1 : position;
        if (holder instanceof SummaryMessageViewHolder) {
            String raw = messages.get(msgPos).getContent();
            // Bỏ prefix "🤖 AI tóm tắt:\n" nếu có (header đã hiện trong layout)
            String display = raw.startsWith("🤖 AI tóm tắt:\n") ? raw.substring("🤖 AI tóm tắt:\n".length()) : raw;
            ((SummaryMessageViewHolder) holder).tvContent.setText(display);
            return;
        }
        if (holder instanceof SystemMessageViewHolder) {
            ((SystemMessageViewHolder) holder).tvContent.setText(messages.get(msgPos).getContent());
            return;
        }
        ChatMessage message = messages.get(msgPos);
        MessageViewHolder messageHolder = (MessageViewHolder) holder;
        long senderId = message.getSenderId();
        boolean isSelf = message.isSentByCurrentUser();

        // Direct room: sender has blocked me
        boolean isMaskedDirect = maskedUserId > 0 && senderId == maskedUserId;
        // Group room: I blocked sender
        boolean isBlockedByMe = !isSelf && groupBlockedByMeIds.contains(senderId);
        // Group room: sender has blocked me → ẩn tên/avatar/nội dung
        boolean hasBlockedMe = !isSelf && groupHasBlockedMeIds.contains(senderId);
        // Group block (either direction): hiển thị collapsed bubble
        boolean isGroupBlocked = !isMaskedDirect && (isBlockedByMe || hasBlockedMe);

        // Reset click/hint state trước khi bind (tránh stale state từ recycled view)
        messageHolder.tvContent.setOnClickListener(null);
        messageHolder.tvContent.setClickable(false);
        if (messageHolder.tvHint != null) messageHolder.tvHint.setVisibility(View.GONE);

        // Sender name
        if (messageHolder.tvSender != null) {
            if (isMaskedDirect || hasBlockedMe) {
                messageHolder.tvSender.setText("Người dùng không khả dụng");
            } else if (isBlockedByMe) {
                messageHolder.tvSender.setText(message.getSenderName() + " · Đã chặn");
            } else {
                messageHolder.tvSender.setText(message.getSenderName());
            }
        }

        // Message content
        if (isGroupBlocked) {
            // Rule 3 & 4: collapsed bubble — user có thể bấm để xem
            boolean isExpanded = expandedMessages.contains(message);
            if (!isExpanded) {
                messageHolder.tvContent.setText("Đã ẩn tin nhắn");
                messageHolder.tvContent.setAlpha(0.7f);
                messageHolder.tvContent.setTypeface(null, android.graphics.Typeface.ITALIC);
                if (messageHolder.tvHint != null) {
                    messageHolder.tvHint.setText("Nhấn để xem");
                    messageHolder.tvHint.setVisibility(View.VISIBLE);
                }
                messageHolder.tvContent.setClickable(true);
                final ChatMessage msg = message;
                messageHolder.tvContent.setOnClickListener(v -> {
                    expandedMessages.add(msg);
                    int pos = messageHolder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
                });
            } else {
                messageHolder.tvContent.setText(message.getContent());
                messageHolder.tvContent.setAlpha(1.0f);
                messageHolder.tvContent.setTypeface(null, android.graphics.Typeface.NORMAL);
                // cho phép collapse lại
                messageHolder.tvContent.setClickable(true);
                final ChatMessage msg = message;
                messageHolder.tvContent.setOnClickListener(v -> {
                    expandedMessages.remove(msg);
                    int pos = messageHolder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
                });
            }
        } else if (isMaskedDirect) {
            messageHolder.tvContent.setText("Tin nhắn không khả dụng");
            messageHolder.tvContent.setAlpha(0.55f);
            messageHolder.tvContent.setTypeface(null, android.graphics.Typeface.ITALIC);
        } else {
            messageHolder.tvContent.setText(message.getContent());
            messageHolder.tvContent.setAlpha(1.0f);
            messageHolder.tvContent.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        messageHolder.tvTime.setText(message.getTimeLabel());

        // Avatar
        boolean maskAvatar = isMaskedDirect || hasBlockedMe;
        if (messageHolder.ivMessageAvatar != null) {
            if (maskAvatar) {
                com.bumptech.glide.Glide.with(context).clear(messageHolder.ivMessageAvatar);
                messageHolder.ivMessageAvatar.setImageResource(R.drawable.ic_user_placeholder);
            } else {
                String avatarUrl = senderId > 0
                        ? RetrofitClient.getCachedAvatarForUser(senderId) : null;
                if (avatarUrl == null || avatarUrl.isEmpty()) {
                    avatarUrl = message.getSenderAvatarUrl();
                }
                if (senderId > 0 && avatarUrl != null && !avatarUrl.isEmpty()) {
                    RetrofitClient.cacheAvatarForUser(senderId, avatarUrl);
                }
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    if (avatarUrl.startsWith("/")) avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                    com.bumptech.glide.Glide.with(context)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_user_placeholder)
                            .error(R.drawable.ic_user_placeholder)
                            .circleCrop()
                            .into(messageHolder.ivMessageAvatar);
                } else {
                    messageHolder.ivMessageAvatar.setImageResource(R.drawable.ic_user_placeholder);
                }
            }
        }

        // Tap avatar/sender-name: chặn khi có quan hệ block bất kỳ chiều nào (kể cả sau khi expand)
        boolean blockClick = isMaskedDirect || isBlockedByMe || hasBlockedMe;
        if (!isSelf && userClickListener != null && senderId > 0 && !blockClick) {
            View.OnClickListener userClick = v -> {
                String av = RetrofitClient.getCachedAvatarForUser(senderId);
                if (av == null || av.isEmpty()) {
                    av = message.getSenderAvatarUrl();
                }
                userClickListener.onUserClick(senderId, message.getSenderName(), av);
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

    private void bindFriendCard(FriendCardViewHolder h) {
        h.tvName.setText(friendCardName);

        // Load avatar
        if (friendCardAvatarUrl != null && !friendCardAvatarUrl.isEmpty()) {
            String url = friendCardAvatarUrl;
            if (url.startsWith("/")) url = RetrofitClient.getBaseUrl() + url.substring(1);
            com.bumptech.glide.Glide.with(context)
                    .load(url)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

        // Action button theo friendship status
        switch (friendStatus) {
            case "PENDING_SENT":
                h.btnAction.setText("Đã gửi lời mời");
                h.btnAction.setEnabled(false);
                h.btnAction.setAlpha(0.5f);
                h.btnAction.setOnClickListener(null);
                break;
            case "PENDING_RECEIVED":
                h.btnAction.setText("Phản hồi");
                h.btnAction.setEnabled(true);
                h.btnAction.setAlpha(1.0f);
                h.btnAction.setOnClickListener(v -> {
                    if (friendCardListener != null) friendCardListener.onRespondToRequest();
                });
                break;
            default: // NONE
                h.btnAction.setText("Thêm bạn bè");
                h.btnAction.setEnabled(true);
                h.btnAction.setAlpha(1.0f);
                h.btnAction.setOnClickListener(v -> {
                    if (friendCardListener != null) friendCardListener.onAddFriend();
                });
                break;
        }

        h.btnViewProfile.setOnClickListener(v -> {
            if (friendCardListener != null) friendCardListener.onViewProfile();
        });
    }

    static class FriendCardViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        MaterialButton btnAction;
        MaterialButton btnViewProfile;

        FriendCardViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivFriendCardAvatar);
            tvName = itemView.findViewById(R.id.tvFriendCardName);
            btnAction = itemView.findViewById(R.id.btnFriendCardAction);
            btnViewProfile = itemView.findViewById(R.id.btnFriendCardViewProfile);
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMessageAvatar;
        TextView tvSender;
        TextView tvContent;
        TextView tvHint; // null cho outgoing messages (không có trong layout)
        TextView tvTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMessageAvatar = itemView.findViewById(R.id.ivMessageAvatar);
            tvSender = itemView.findViewById(R.id.tvMessageSender);
            tvContent = itemView.findViewById(R.id.tvMessageContent);
            tvHint = itemView.findViewById(R.id.tvMessageHint);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
        }
    }

    static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;

        SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvSystemMessage);
        }
    }

    static class SummaryMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;

        SummaryMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvSummaryContent);
        }
    }
}
