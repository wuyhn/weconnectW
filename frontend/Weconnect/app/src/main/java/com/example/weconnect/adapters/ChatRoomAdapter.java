package com.example.weconnect.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup.MarginLayoutParams;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.models.ChatRoom;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomAdapter extends RecyclerView.Adapter<ChatRoomAdapter.ChatRoomViewHolder> {

    public interface OnChatRoomClickListener {
        void onChatRoomClick(ChatRoom room);
    }

    private final List<ChatRoom> rooms = new ArrayList<>();
    private final OnChatRoomClickListener listener;

    public ChatRoomAdapter(OnChatRoomClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ChatRoom> newRooms) {
        rooms.clear();
        rooms.addAll(newRooms);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatRoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ChatRoomViewHolder(view);
    }

    /** Bỏ emoji/ký tự đặc biệt ở đầu chuỗi (vd: "🎵 Âm nhạc" → "Âm nhạc") */
    private static String stripLeadingEmoji(String text) {
        if (text == null) return "";
        return text.replaceFirst("^[^\\p{L}\\p{N}]+", "").trim();
    }

    private static boolean isGroupType(String type) {
        return ChatRoom.TYPE_ACTIVITY.equals(type)
                || ChatRoom.TYPE_GROUP.equals(type)
                || ChatRoom.TYPE_FRIEND_GROUP.equals(type);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatRoomViewHolder holder, int position) {
        ChatRoom room = rooms.get(position);
        String type = room.getType();

        boolean isGroup = isGroupType(type);
        boolean isMessageRequest = ChatRoom.TYPE_MESSAGE_REQUESTS.equals(type);
        boolean hasUnread = room.getUnreadCount() > 0;

        // ── Post status badge ──
        String postStatus = room.getPostStatusLabel();
        if (postStatus != null && !postStatus.isEmpty()) {
            holder.tvTypeBadge.setText(postStatus);
            holder.tvTypeBadge.setVisibility(View.VISIBLE);
            holder.tvTypeBadge.setBackgroundResource(R.drawable.bg_chat_type_badge_inactive);
        } else {
            holder.tvTypeBadge.setText("");
            holder.tvTypeBadge.setVisibility(View.GONE);
        }

        // ── Unread count badge (chỉ dùng cho message_requests) ──
        if (isMessageRequest && room.getRequestCount() > 0) {
            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
            holder.tvUnreadBadge.setText(room.getRequestCount() > 99 ? "99+" : String.valueOf(room.getRequestCount()));
        } else {
            holder.tvUnreadBadge.setText("");
            holder.tvUnreadBadge.setVisibility(View.GONE);
        }

        // ── Unread dot ──
        if (!isMessageRequest) {
            if (hasUnread) {
                holder.viewAccentDot.setBackgroundResource(R.drawable.bg_chat_accent_dot);
                holder.viewAccentDot.setVisibility(View.VISIBLE);
            } else if (isGroup && !room.isActive()) {
                // Group đã kết thúc: chấm xám
                holder.viewAccentDot.setBackgroundResource(R.drawable.bg_chat_accent_dot_inactive);
                holder.viewAccentDot.setVisibility(View.VISIBLE);
            } else {
                holder.viewAccentDot.setVisibility(View.GONE);
            }
        } else {
            holder.viewAccentDot.setVisibility(View.GONE);
        }

        // ── Margin trái của content: 0 cho group (không có avatar), 14dp cho direct ──
        MarginLayoutParams mlp = (MarginLayoutParams) holder.layoutChatContent.getLayoutParams();
        mlp.setMarginStart(isGroup ? 0 : (int) (14 * holder.itemView.getContext().getResources().getDisplayMetrics().density + 0.5f));
        holder.layoutChatContent.setLayoutParams(mlp);

        if (isGroup) {
            // ── GROUP / ACTIVITY layout ──
            holder.frameAvatar.setVisibility(View.GONE);
            holder.tvChatTime.setVisibility(View.GONE);
            holder.tvActivityStatus.setVisibility(View.GONE);
            holder.layoutLastMessage.setVisibility(View.GONE);
            holder.viewOnlineIndicator.setVisibility(View.GONE);

            holder.tvUserName.setText(stripLeadingEmoji(room.getTitle()));

            // Chủ phòng
            holder.tvGroupOwner.setVisibility(View.VISIBLE);
            String owner = room.getOwnerUsername();
            holder.tvGroupOwner.setText("Chủ phòng: " + (owner != null && !owner.isEmpty() ? owner : "Không rõ"));

            // Ngày hoạt động (chỉ có với TYPE_ACTIVITY)
            String dateDisplay = room.getActivityDateDisplay();
            if (dateDisplay != null && !dateDisplay.isEmpty()) {
                holder.tvGroupDate.setText("Ngày: " + dateDisplay);
                holder.tvGroupDate.setVisibility(View.VISIBLE);
            } else {
                holder.tvGroupDate.setVisibility(View.GONE);
            }

        } else {
            // ── DIRECT / MESSAGE_REQUESTS layout ──
            holder.frameAvatar.setVisibility(View.VISIBLE);
            holder.tvChatTime.setVisibility(View.VISIBLE);
            holder.layoutLastMessage.setVisibility(View.VISIBLE);
            holder.tvGroupOwner.setVisibility(View.GONE);
            holder.tvGroupDate.setVisibility(View.GONE);

            boolean blockedByOther = ChatRoom.TYPE_DIRECT.equals(type) && room.hasBlockedMe();

            if (blockedByOther) {
                holder.ivUserAvatar.setImageResource(R.drawable.ic_user_placeholder);
                holder.tvUserName.setText("Người dùng không tồn tại");
            } else {
                if (room.getAvatarUrl() != null && !room.getAvatarUrl().isEmpty()) {
                    String avatarUrl = room.getAvatarUrl();
                    if (avatarUrl.startsWith("/")) {
                        avatarUrl = com.example.weconnect.api.RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                    }
                    com.bumptech.glide.Glide.with(holder.itemView.getContext())
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_user_placeholder)
                            .error(R.drawable.ic_user_placeholder)
                            .circleCrop()
                            .into(holder.ivUserAvatar);
                } else {
                    holder.ivUserAvatar.setImageResource(room.getAvatarResId());
                }
                holder.tvUserName.setText(room.getTitle());
            }

            holder.tvLastMessage.setText(room.getLastMessagePreview());
            holder.tvChatTime.setText(room.getLastMessageTime());

            if (isMessageRequest) {
                holder.viewOnlineIndicator.setVisibility(View.GONE);
                holder.tvActivityStatus.setVisibility(View.GONE);
            } else {
                // Direct room: trạng thái online / last active
                boolean canShow = !blockedByOther && room.isFriend() && !room.isBlockedByMe();
                if (canShow && room.isOtherUserOnline()) {
                    holder.viewOnlineIndicator.setVisibility(View.VISIBLE);
                    holder.tvActivityStatus.setVisibility(View.GONE);
                } else if (canShow && room.getOtherUserLastActiveMins() != null) {
                    holder.viewOnlineIndicator.setVisibility(View.GONE);
                    long mins = room.getOtherUserLastActiveMins();
                    String text = mins < 60
                            ? "Đã hoạt động " + Math.max(1, mins) + " phút trước"
                            : "Đã hoạt động " + (mins / 60) + " giờ trước";
                    holder.tvActivityStatus.setText(text);
                    holder.tvActivityStatus.setVisibility(View.VISIBLE);
                } else {
                    holder.viewOnlineIndicator.setVisibility(View.GONE);
                    holder.tvActivityStatus.setVisibility(View.GONE);
                }
            }
        }

        holder.itemView.setOnClickListener(v -> listener.onChatRoomClick(room));
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    static class ChatRoomViewHolder extends RecyclerView.ViewHolder {
        FrameLayout frameAvatar;
        LinearLayout layoutChatContent;
        ImageView ivUserAvatar;
        TextView tvUserName;
        TextView tvChatTime;
        TextView tvTypeBadge;
        TextView tvLastMessage;
        TextView tvActivityStatus;
        TextView tvUnreadBadge;
        View viewAccentDot;
        View viewOnlineIndicator;
        LinearLayout layoutLastMessage;
        TextView tvGroupOwner;
        TextView tvGroupDate;

        ChatRoomViewHolder(@NonNull View itemView) {
            super(itemView);
            frameAvatar = itemView.findViewById(R.id.frameAvatar);
            layoutChatContent = itemView.findViewById(R.id.layoutChatContent);
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvChatTime = itemView.findViewById(R.id.tvChatTime);
            tvTypeBadge = itemView.findViewById(R.id.tvChatTypeBadge);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvActivityStatus = itemView.findViewById(R.id.tvActivityStatus);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
            viewAccentDot = itemView.findViewById(R.id.viewAccentDot);
            viewOnlineIndicator = itemView.findViewById(R.id.viewOnlineIndicator);
            layoutLastMessage = itemView.findViewById(R.id.layoutLastMessage);
            tvGroupOwner = itemView.findViewById(R.id.tvGroupOwner);
            tvGroupDate = itemView.findViewById(R.id.tvGroupDate);
        }
    }
}
