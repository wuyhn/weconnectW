package com.example.weconnect.notification.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.social.ui.PendingListActivity;
import com.example.weconnect.profile.ui.UserProfileActivity;
import com.example.weconnect.notification.data.NotificationApiService;
import com.example.weconnect.post.data.PostApiService;
import com.example.weconnect.core.data.RetrofitClient;
import com.example.weconnect.notification.data.FakeNotificationRepository;
import com.example.weconnect.data.FakeNotificationRepository.NotificationType;
import com.example.weconnect.social.data.FakeSocialRepository;
import com.example.weconnect.core.data.ApiResponse;
import com.example.weconnect.notification.data.NotificationItem;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM_REAL = 1;
    private static final int TYPE_ITEM_FAKE = 2;

    private final Context context;
    private final List<Object> items; // String (date header), NotificationItem (real), or FakeNotificationRepository.NotificationItem

    public NotificationAdapter(Context context, List<Object> items) {
        this.context = context;
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof String) return TYPE_HEADER;
        if (item instanceof NotificationItem) return TYPE_ITEM_REAL;
        return TYPE_ITEM_FAKE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            TextView tv = new TextView(context);
            tv.setTextSize(15);
            tv.setTextColor(context.getResources().getColor(R.color.text_primary, null));
            tv.setPadding(56, 32, 20, 8);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            return new HeaderViewHolder(tv);
        }
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotifViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvHeader.setText((String) items.get(position));
        } else if (holder instanceof NotifViewHolder) {
            Object item = items.get(position);
            if (item instanceof NotificationItem) {
                bindRealNotification((NotifViewHolder) holder, (NotificationItem) item);
            } else if (item instanceof FakeNotificationRepository.NotificationItem) {
                bindFakeNotification((NotifViewHolder) holder, 
                        (FakeNotificationRepository.NotificationItem) item);
            }
        }
    }

    private void bindRealNotification(NotifViewHolder holder, NotificationItem item) {
        holder.tvMessage.setText(item.getMessage());
        holder.tvTime.setText(formatCreatedAt(item.getCreatedAt()));

        // Load avatar from server URL with Glide
        String avatarUrl = item.getSenderAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (avatarUrl.startsWith("/")) {
                avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
            }
            com.bumptech.glide.Glide.with(context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

        // Determine if actionable
        boolean isActionable = (item.getType() == NotificationItem.NotificationType.JOIN_REQUEST
                || item.getType() == NotificationItem.NotificationType.FRIEND_REQUEST_RECEIVED);

        if (isActionable && !item.isActioned()) {
            // For JOIN_REQUEST, check if the related post has expired
            if (item.getType() == NotificationItem.NotificationType.JOIN_REQUEST
                    && item.getRelatedPostId() != null) {
                // Initially hide actions until we know the post status
                holder.layoutActions.setVisibility(View.GONE);
                holder.tvActioned.setVisibility(View.GONE);

                RetrofitClient.loadToken(context);
                PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);
                postApi.getPost(item.getRelatedPostId()).enqueue(new Callback<ApiResponse<com.example.weconnect.models.PostResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<com.example.weconnect.models.PostResponse>> call,
                                           Response<ApiResponse<com.example.weconnect.models.PostResponse>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            com.example.weconnect.models.PostResponse post = response.body().getResult();
                            if (post.isExpired()) {
                                // Post đã hết hạn - hiện label "Đã hết hạn"
                                holder.layoutActions.setVisibility(View.GONE);
                                holder.tvActioned.setVisibility(View.VISIBLE);
                                holder.tvActioned.setText("⏰ Đã hết hạn");
                                holder.tvActioned.setTextColor(context.getResources().getColor(R.color.text_secondary, null));
                                // Auto-mark as actioned
                                markNotificationActioned(item, holder, null);
                            } else {
                                // Post còn hạn - hiện nút bình thường
                                showJoinRequestActions(holder, item);
                            }
                        } else {
                            // Cannot check, show actions normally
                            showJoinRequestActions(holder, item);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<com.example.weconnect.models.PostResponse>> call, Throwable t) {
                        // Cannot check, show actions normally
                        showJoinRequestActions(holder, item);
                    }
                });
            } else {
                // FRIEND_REQUEST or JOIN_REQUEST without postId
                holder.layoutActions.setVisibility(View.VISIBLE);
                holder.tvActioned.setVisibility(View.GONE);

                holder.btnAccept.setText("Chấp nhận");
                holder.btnDecline.setText("Từ chối");

                if (item.getType() == NotificationItem.NotificationType.FRIEND_REQUEST_RECEIVED
                        && item.getRelatedUserId() != null) {
                    // Use real backend API
                    com.example.weconnect.api.FriendApiService friendApi =
                            RetrofitClient.getClient().create(com.example.weconnect.api.FriendApiService.class);

                    holder.btnAccept.setOnClickListener(v -> {
                        friendApi.acceptFriend(item.getRelatedUserId()).enqueue(new Callback<ApiResponse<Void>>() {
                            @Override
                            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                                markNotificationActioned(item, holder, "Đã chấp nhận lời mời kết bạn");
                            }
                            @Override
                            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });

                    holder.btnDecline.setOnClickListener(v -> {
                        friendApi.declineFriend(item.getRelatedUserId()).enqueue(new Callback<ApiResponse<Void>>() {
                            @Override
                            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                                markNotificationActioned(item, holder, "Đã từ chối lời mời kết bạn");
                            }
                            @Override
                            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                } else {
                    // Fallback for other types
                    holder.btnAccept.setOnClickListener(v -> {
                        FakeSocialRepository.getInstance().acceptFriendRequest(item.getRelatedUsername());
                        markNotificationActioned(item, holder, "Đã chấp nhận");
                    });
                    holder.btnDecline.setOnClickListener(v -> {
                        FakeSocialRepository.getInstance().declineFriendRequest(item.getRelatedUsername());
                        markNotificationActioned(item, holder, "Đã từ chối");
                    });
                }
            }
        } else if (isActionable && item.isActioned()) {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.VISIBLE);
            holder.tvActioned.setText("✅ Đã xử lý");
        } else {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.GONE);
        }

        // Click item -> navigate based on type + mark as read
        holder.itemView.setOnClickListener(v -> {
            // Mark as read on backend if not already read
            if (!item.isRead()) {
                item.setRead(true);
                holder.viewUnreadDot.setVisibility(View.GONE);
                holder.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);

                NotificationApiService notifApi = RetrofitClient.getClient()
                        .create(NotificationApiService.class);
                notifApi.markAsRead(item.getId()).enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {}
                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {}
                });
            }

            if (item.getRelatedPostId() != null) {
                navigateToPostDetail(item);
            } else if (item.getRelatedUsername() != null && !item.getRelatedUsername().isEmpty()) {
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("username", item.getRelatedUsername());
                intent.putExtra("view_other", true);
                if (item.getRelatedUserId() != null) {
                    intent.putExtra("user_id", item.getRelatedUserId().longValue());
                }
                context.startActivity(intent);
            }
        });

        // Unread indicator
        if (!item.isRead()) {
            holder.viewUnreadDot.setVisibility(View.VISIBLE);
            holder.tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            holder.viewUnreadDot.setVisibility(View.GONE);
            holder.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void showJoinRequestActions(NotifViewHolder holder, NotificationItem item) {
        holder.layoutActions.setVisibility(View.VISIBLE);
        holder.tvActioned.setVisibility(View.GONE);

        holder.btnAccept.setText("✅ Duyệt nhanh");
        holder.btnDecline.setText("👤 Xem Profile");

        holder.btnAccept.setOnClickListener(v -> approveJoinRequest(item, holder));

        holder.btnDecline.setOnClickListener(v -> {
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("username", item.getRelatedUsername());
            intent.putExtra("view_other", true);
            if (item.getRelatedUserId() != null) {
                intent.putExtra("user_id", item.getRelatedUserId().longValue());
            }
            context.startActivity(intent);
        });
    }

    private void approveJoinRequest(NotificationItem item, NotifViewHolder holder) {
        if (item.getRelatedPostId() == null || item.getRelatedUserId() == null) {
            Toast.makeText(context, "Thiếu thông tin để duyệt", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.loadToken(context);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);

        postApi.approveMember(item.getRelatedPostId(), item.getRelatedUserId())
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> response) {
                        if (response.isSuccessful()) {
                            markNotificationActioned(item, holder,
                                    "✅ Bạn đã duyệt " + item.getRelatedUsername());

                            // Backend đã tự thêm user vào phòng chat hoạt động

                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                    .setTitle("Đã duyệt!")
                                    .setMessage("Bạn đã duyệt " + item.getRelatedUsername() + " tham gia hoạt động.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        } else {
                            Toast.makeText(context, "Lỗi khi duyệt thành viên", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void markNotificationActioned(NotificationItem item, NotifViewHolder holder, String message) {
        item.setActioned(true);
        item.setRead(true);

        // Mark on backend
        NotificationApiService notifApi = RetrofitClient.getClient()
                .create(NotificationApiService.class);
        notifApi.markAsActioned(item.getId()).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {}
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {}
        });

        notifyItemChanged(holder.getAdapterPosition());
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToPostDetail(NotificationItem item) {
        if (item.getRelatedPostId() == null) return;

        RetrofitClient.loadToken(context);
        com.example.weconnect.api.PostApiService postApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.PostApiService.class);

        postApi.getPost(item.getRelatedPostId()).enqueue(new Callback<ApiResponse<com.example.weconnect.models.PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<com.example.weconnect.models.PostResponse>> call,
                                   Response<ApiResponse<com.example.weconnect.models.PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    com.example.weconnect.models.Post post = response.body().getResult().toPost();

                    // If this is a JOIN_APPROVED notification, mark post as joined
                    // Backend đã tự thêm user vào phòng chat hoạt động khi approve
                    if (item.getType() == NotificationItem.NotificationType.JOIN_APPROVED) {
                        post.setJoined(true);
                    }

                    Intent intent = new Intent(context, com.example.weconnect.activities.PostDetailActivity.class);
                    intent.putExtra("post", post);

                    // If this is a JOIN_REQUEST and NOT yet actioned, tell PostDetailActivity to show approve/reject
                    if (item.getType() == NotificationItem.NotificationType.JOIN_REQUEST
                            && !item.isActioned()) {
                        intent.putExtra("show_pending_actions", true);
                        if (item.getRelatedUserId() != null) {
                            intent.putExtra("pending_user_id", item.getRelatedUserId().longValue());
                        }
                        intent.putExtra("pending_username", item.getRelatedUsername());
                    }

                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "Không thể tải bài viết", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<com.example.weconnect.models.PostResponse>> call, Throwable t) {
                Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindFakeNotification(NotifViewHolder holder, FakeNotificationRepository.NotificationItem item) {
        holder.tvMessage.setText(item.getMessage());
        holder.tvTime.setText(formatTime(item.getTimestamp()));

        // Show action buttons for actionable items
        boolean isActionable = (item.getType() == NotificationType.FRIEND_REQUEST_RECEIVED
                || item.getType() == NotificationType.JOIN_REQUEST);

        if (isActionable && !item.isActioned()) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.tvActioned.setVisibility(View.GONE);

            holder.btnAccept.setText("Chấp nhận");
            holder.btnDecline.setText("Từ chối");

            holder.btnAccept.setOnClickListener(v -> {
                if (item.getType() == NotificationType.FRIEND_REQUEST_RECEIVED) {
                    FakeSocialRepository.getInstance().acceptFriendRequest(item.getRelatedUsername());
                    Toast.makeText(context, "Đã chấp nhận lời mời kết bạn", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Đã duyệt yêu cầu tham gia", Toast.LENGTH_SHORT).show();
                }
                item.setActioned(true);
                notifyItemChanged(holder.getAdapterPosition());
            });

            holder.btnDecline.setOnClickListener(v -> {
                if (item.getType() == NotificationType.FRIEND_REQUEST_RECEIVED) {
                    FakeSocialRepository.getInstance().declineFriendRequest(item.getRelatedUsername());
                    Toast.makeText(context, "Đã từ chối lời mời kết bạn", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Đã từ chối yêu cầu tham gia", Toast.LENGTH_SHORT).show();
                }
                item.setActioned(true);
                notifyItemChanged(holder.getAdapterPosition());
            });
        } else if (isActionable && item.isActioned()) {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.VISIBLE);
            holder.tvActioned.setText("Đã xử lý");
        } else {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.GONE);
        }

        // Click to open profile
        if (item.getRelatedUsername() != null && !item.getRelatedUsername().isEmpty()) {
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("username", item.getRelatedUsername());
                intent.putExtra("view_other", true);
                context.startActivity(intent);
            });
        }

        // Unread indicator
        if (!item.isRead()) {
            holder.viewUnreadDot.setVisibility(View.VISIBLE);
            holder.tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            holder.viewUnreadDot.setVisibility(View.GONE);
            holder.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private String formatCreatedAt(String createdAt) {
        if (createdAt == null) return "";
        try {
            // Parse ISO datetime
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(createdAt);
            java.time.Duration duration = java.time.Duration.between(dateTime, java.time.LocalDateTime.now());
            long minutes = duration.toMinutes();
            long hours = duration.toHours();
            long days = duration.toDays();

            if (minutes < 1) return "Vừa xong";
            if (minutes < 60) return minutes + " phút trước";
            if (hours < 24) return hours + " giờ trước";
            if (days < 7) return days + " ngày trước";

            java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return dateTime.format(formatter);
        } catch (Exception e) {
            return createdAt;
        }
    }

    private String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);

        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        if (hours < 24) return hours + " giờ trước";

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void markAllRead() {
        for (Object item : items) {
            if (item instanceof NotificationItem) {
                ((NotificationItem) item).setRead(true);
            } else if (item instanceof FakeNotificationRepository.NotificationItem) {
                ((FakeNotificationRepository.NotificationItem) item).setRead(true);
            }
        }
        notifyDataSetChanged();
    }

    // Group real notifications by date
    public static List<Object> groupByDate(List<NotificationItem> notifications) {
        List<Object> grouped = new ArrayList<>();
        String lastDateLabel = "";

        for (NotificationItem item : notifications) {
            String dateLabel = getDateLabelFromCreatedAt(item.getCreatedAt());
            if (!dateLabel.equals(lastDateLabel)) {
                grouped.add(dateLabel);
                lastDateLabel = dateLabel;
            }
            grouped.add(item);
        }
        return grouped;
    }

    // Group fake notifications by date
    public static List<Object> groupByDateFake(List<FakeNotificationRepository.NotificationItem> notifications) {
        List<Object> grouped = new ArrayList<>();
        String lastDateLabel = "";

        for (FakeNotificationRepository.NotificationItem item : notifications) {
            String dateLabel = getDateLabel(item.getTimestamp());
            if (!dateLabel.equals(lastDateLabel)) {
                grouped.add(dateLabel);
                lastDateLabel = dateLabel;
            }
            grouped.add(item);
        }
        return grouped;
    }

    private static String getDateLabelFromCreatedAt(String createdAt) {
        if (createdAt == null) return "Khác";
        try {
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(createdAt);
            java.time.LocalDate date = dateTime.toLocalDate();
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate yesterday = today.minusDays(1);

            if (date.equals(today)) return "Hôm nay";
            if (date.equals(yesterday)) return "Hôm qua";

            java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return date.format(formatter);
        } catch (Exception e) {
            return "Khác";
        }
    }

    private static String getDateLabel(long timestamp) {
        Calendar notifCal = Calendar.getInstance();
        notifCal.setTimeInMillis(timestamp);

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        if (sameDay(notifCal, today)) return "Hôm nay";
        if (sameDay(notifCal, yesterday)) return "Hôm qua";

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(TextView tv) {
            super(tv);
            tvHeader = tv;
        }
    }

    static class NotifViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvMessage, tvTime, tvActioned;
        View viewUnreadDot;
        LinearLayout layoutActions;
        MaterialButton btnAccept, btnDecline;

        NotifViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivNotifAvatar);
            tvMessage = itemView.findViewById(R.id.tvNotifMessage);
            tvTime = itemView.findViewById(R.id.tvNotifTime);
            tvActioned = itemView.findViewById(R.id.tvNotifActioned);
            viewUnreadDot = itemView.findViewById(R.id.viewUnreadDot);
            layoutActions = itemView.findViewById(R.id.layoutNotifActions);
            btnAccept = itemView.findViewById(R.id.btnNotifAccept);
            btnDecline = itemView.findViewById(R.id.btnNotifDecline);
        }
    }
}
