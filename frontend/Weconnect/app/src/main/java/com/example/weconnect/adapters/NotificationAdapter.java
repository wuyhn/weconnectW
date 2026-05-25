package com.example.weconnect.adapters;

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
import com.example.weconnect.activities.PendingListActivity;
import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.api.NotificationApiService;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.data.FakeNotificationRepository;
import com.example.weconnect.data.FakeNotificationRepository.NotificationType;
import com.example.weconnect.data.FakeSocialRepository;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.NotificationItem;
import com.example.weconnect.util.BadgeManager;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM_REAL = 1;
    private static final int TYPE_ITEM_FAKE = 2;
    private static final int TYPE_JOIN_REQUEST_GROUP = 3;

    private final Context context;
    private final List<Object> items; // String (date header), NotificationItem, JoinRequestGroup, or FakeNotificationRepository.NotificationItem
    private final Runnable onBadgeChanged;

    /** Đại diện cho tất cả JOIN_REQUEST notifications của một bài post. */
    public static class JoinRequestGroup {
        public final Long relatedPostId;
        public final String postTitle;
        public final int requestCount;
        public final int unreadCount;
        public final String latestCreatedAt;
        public boolean hasUnread;

        public JoinRequestGroup(Long relatedPostId, String postTitle, int requestCount,
                                int unreadCount, String latestCreatedAt) {
            this.relatedPostId = relatedPostId;
            this.postTitle = postTitle;
            this.requestCount = requestCount;
            this.unreadCount = unreadCount;
            this.latestCreatedAt = latestCreatedAt;
            this.hasUnread = unreadCount > 0;
        }
    }

    public NotificationAdapter(Context context, List<Object> items) {
        this(context, items, null);
    }

    public NotificationAdapter(Context context, List<Object> items, Runnable onBadgeChanged) {
        this.context = context;
        this.items = items;
        this.onBadgeChanged = onBadgeChanged;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof String) return TYPE_HEADER;
        if (item instanceof NotificationItem) return TYPE_ITEM_REAL;
        if (item instanceof JoinRequestGroup) return TYPE_JOIN_REQUEST_GROUP;
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
            } else if (item instanceof JoinRequestGroup) {
                bindJoinRequestGroup((NotifViewHolder) holder, (JoinRequestGroup) item);
            } else if (item instanceof FakeNotificationRepository.NotificationItem) {
                bindFakeNotification((NotifViewHolder) holder,
                        (FakeNotificationRepository.NotificationItem) item);
            }
        }
    }

    private void bindJoinRequestGroup(NotifViewHolder holder, JoinRequestGroup group) {
        String titleText = group.postTitle != null && !group.postTitle.isEmpty()
                ? "Yêu cầu tham gia: " + group.postTitle
                : "Yêu cầu tham gia";
        holder.tvMessage.setText(titleText);

        String subtitleText = group.requestCount == 1
                ? "1 yêu cầu đang chờ duyệt"
                : group.requestCount + " yêu cầu đang chờ duyệt";
        holder.tvSubtitle.setText(subtitleText);
        holder.tvSubtitle.setVisibility(View.VISIBLE);

        holder.tvTime.setText(formatCreatedAt(group.latestCreatedAt));

        holder.ivAvatar.setImageResource(R.drawable.ic_user_placeholder);

        holder.layoutActions.setVisibility(View.GONE);
        holder.tvActioned.setVisibility(View.GONE);

        // Unread indicator
        if (group.hasUnread) {
            holder.viewUnreadDot.setVisibility(View.VISIBLE);
            holder.tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            holder.viewUnreadDot.setVisibility(View.GONE);
            holder.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        holder.itemView.setOnClickListener(v -> {
            if (group.hasUnread) {
                group.hasUnread = false;
                holder.viewUnreadDot.setVisibility(View.GONE);
                holder.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                BadgeManager.decrement(group.unreadCount);
                if (onBadgeChanged != null) onBadgeChanged.run();

                RetrofitClient.loadToken(context);
                NotificationApiService notifApi = RetrofitClient.getClient()
                        .create(NotificationApiService.class);
                notifApi.markJoinRequestsRead(group.relatedPostId)
                        .enqueue(new Callback<ApiResponse<Void>>() {
                            @Override public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {}
                            @Override public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {}
                        });
            }

            Intent intent = new Intent(context, PendingListActivity.class);
            intent.putExtra("post_id", group.relatedPostId);
            if (group.postTitle != null) intent.putExtra("post_title", group.postTitle);
            context.startActivity(intent);
        });
    }

    private void bindRealNotification(NotifViewHolder holder, NotificationItem item) {
        holder.tvMessage.setText(item.getMessage());
        holder.tvSubtitle.setVisibility(View.GONE);
        holder.tvTime.setText(formatCreatedAt(item.getCreatedAt()));

        // Load avatar — if sender is current user, use fresh cached URL instead of stale stored URL
        String avatarUrl = item.getSenderAvatarUrl();
        long myId = RetrofitClient.getUserId(context);
        if (myId > 0 && item.getRelatedUserId() != null && item.getRelatedUserId() == myId) {
            String cached = RetrofitClient.getAvatarUrl(context);
            if (cached != null && !cached.isEmpty()) avatarUrl = cached;
        }
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
        boolean isActionable = (item.getType() == NotificationItem.NotificationType.FRIEND_REQUEST_RECEIVED);

        if (isActionable && !item.isActioned()) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.tvActioned.setVisibility(View.GONE);

            holder.btnAccept.setText("Chấp nhận");
            holder.btnDecline.setText("Từ chối");

            if (item.getRelatedUserId() != null) {
                com.example.weconnect.api.FriendApiService friendApi =
                        RetrofitClient.getClient().create(com.example.weconnect.api.FriendApiService.class);

                holder.btnAccept.setOnClickListener(v -> {
                    friendApi.acceptFriend(item.getRelatedUserId()).enqueue(new Callback<ApiResponse<Void>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                            if (response.isSuccessful()) {
                                markNotificationActioned(item, holder, "Đã chấp nhận lời mời kết bạn", "ACCEPTED");
                            } else {
                                // Lời mời đã được xử lý trước đó (ví dụ từ màn Profile)
                                Toast.makeText(context, "Lời mời này đã được xử lý.", Toast.LENGTH_SHORT).show();
                                markNotificationActioned(item, holder, null, null);
                            }
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
                            if (response.isSuccessful()) {
                                markNotificationActioned(item, holder, "Đã từ chối lời mời kết bạn", "DECLINED");
                            } else {
                                // Lời mời đã được xử lý trước đó (ví dụ từ màn Profile)
                                Toast.makeText(context, "Lời mời này đã được xử lý.", Toast.LENGTH_SHORT).show();
                                markNotificationActioned(item, holder, null, null);
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                            Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            } else {
                holder.btnAccept.setOnClickListener(v -> {
                    FakeSocialRepository.getInstance().acceptFriendRequest(item.getRelatedUsername());
                    markNotificationActioned(item, holder, "Đã chấp nhận lời mời kết bạn", "ACCEPTED");
                });
                holder.btnDecline.setOnClickListener(v -> {
                    FakeSocialRepository.getInstance().declineFriendRequest(item.getRelatedUsername());
                    markNotificationActioned(item, holder, "Đã từ chối lời mời kết bạn", "DECLINED");
                });
            }
        } else if (isActionable && item.isActioned()) {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.VISIBLE);
            holder.tvActioned.setText(getActionedText(item));
        } else {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.GONE);
        }

        // Click item -> navigate based on type + mark as read
        holder.itemView.setOnClickListener(v -> {
            if (!item.isRead()) {
                item.setRead(true);
                holder.viewUnreadDot.setVisibility(View.GONE);
                holder.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                BadgeManager.decrement();
                if (onBadgeChanged != null) onBadgeChanged.run();

                NotificationApiService notifApi = RetrofitClient.getClient()
                        .create(NotificationApiService.class);
                notifApi.markAsRead(item.getId()).enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {}
                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {}
                });
            }

            if ((item.getType() == NotificationItem.NotificationType.REPORT_CONFIRMED
                    || item.getType() == NotificationItem.NotificationType.REPORT_PENALTY)
                    && item.getRelatedReportId() != null) {
                com.example.weconnect.utils.ReportPenaltyDetailBottomSheet.show(
                        context, item.getRelatedReportId());
            } else if (item.getType() == NotificationItem.NotificationType.ADMIN_WARNING
                    && item.getRelatedReportId() != null) {
                android.content.Intent intent = new android.content.Intent(context,
                        com.example.weconnect.activities.ReportPenaltyDetailActivity.class);
                intent.putExtra("report_id", item.getRelatedReportId().longValue());
                context.startActivity(intent);
            } else if (item.getRelatedPostId() != null) {
                navigateToPostDetail(item);
            } else if (item.getType() == NotificationItem.NotificationType.FRIEND_REQUEST_RECEIVED
                    && item.getRelatedUserId() != null) {
                RetrofitClient.loadToken(context);
                com.example.weconnect.api.FriendApiService blockCheckApi =
                        RetrofitClient.getClient().create(com.example.weconnect.api.FriendApiService.class);
                blockCheckApi.getBlockStatus(item.getRelatedUserId()).enqueue(
                        new Callback<ApiResponse<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<java.util.Map<String, Object>>> call,
                                           Response<ApiResponse<java.util.Map<String, Object>>> response) {
                        boolean blocked = false;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            Object val = response.body().getResult().get("isBlockedBetweenUsers");
                            blocked = Boolean.TRUE.equals(val);
                        }
                        if (blocked) {
                            Toast.makeText(context, "Nội dung này không hiển thị.", Toast.LENGTH_SHORT).show();
                        } else if (item.getRelatedUsername() != null && !item.getRelatedUsername().isEmpty()) {
                            Intent intent = new Intent(context, UserProfileActivity.class);
                            intent.putExtra("username", item.getRelatedUsername());
                            intent.putExtra("view_other", true);
                            intent.putExtra("user_id", item.getRelatedUserId().longValue());
                            context.startActivity(intent);
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                        if (item.getRelatedUsername() != null && !item.getRelatedUsername().isEmpty()) {
                            Intent intent = new Intent(context, UserProfileActivity.class);
                            intent.putExtra("username", item.getRelatedUsername());
                            intent.putExtra("view_other", true);
                            intent.putExtra("user_id", item.getRelatedUserId().longValue());
                            context.startActivity(intent);
                        }
                    }
                });
            } else if (item.getRelatedUsername() != null && !item.getRelatedUsername().isEmpty()
                    && item.getType() != NotificationItem.NotificationType.ADMIN_WARNING
                    && item.getType() != NotificationItem.NotificationType.ADMIN_ACTION) {
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

    private void markNotificationActioned(NotificationItem item, NotifViewHolder holder,
            String toastMessage, String actionResult) {
        boolean wasUnread = !item.isRead();
        item.setActioned(true);
        item.setRead(true);
        if (actionResult != null) {
            item.setActionResult(actionResult);
        }
        if (wasUnread) {
            BadgeManager.decrement();
            if (onBadgeChanged != null) onBadgeChanged.run();
        }

        NotificationApiService notifApi = RetrofitClient.getClient()
                .create(NotificationApiService.class);
        notifApi.markAsActioned(item.getId(), actionResult).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {}
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {}
        });

        notifyItemChanged(holder.getAdapterPosition());
        if (toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private String getActionedText(NotificationItem item) {
        if (item.getType() == NotificationItem.NotificationType.FRIEND_REQUEST_RECEIVED) {
            if ("ACCEPTED".equals(item.getActionResult())) return "Đã chấp nhận lời mời kết bạn";
            if ("DECLINED".equals(item.getActionResult())) return "Đã từ chối lời mời kết bạn";
        }
        return "Đã xử lý";
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

                    if (item.getType() == NotificationItem.NotificationType.JOIN_APPROVED) {
                        post.setJoined(true);
                    }

                    Intent intent = new Intent(context, com.example.weconnect.activities.PostDetailActivity.class);
                    intent.putExtra("post", post);
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
        holder.tvSubtitle.setVisibility(View.GONE);
        holder.tvTime.setText(formatTime(item.getTimestamp()));

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

        holder.itemView.setOnClickListener(v -> {
            if (!item.isRead()) {
                item.setRead(true);
                holder.viewUnreadDot.setVisibility(View.GONE);
                holder.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                BadgeManager.decrement();
                if (onBadgeChanged != null) onBadgeChanged.run();
            }
            if (item.getRelatedUsername() != null && !item.getRelatedUsername().isEmpty()) {
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("username", item.getRelatedUsername());
                intent.putExtra("view_other", true);
                context.startActivity(intent);
            }
        });

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
            } else if (item instanceof JoinRequestGroup) {
                ((JoinRequestGroup) item).hasUnread = false;
            } else if (item instanceof FakeNotificationRepository.NotificationItem) {
                ((FakeNotificationRepository.NotificationItem) item).setRead(true);
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Group real notifications by date.
     * JOIN_REQUEST notifications (unactioned) are grouped by postId into JoinRequestGroup items.
     * Each group is sorted by its latest request time and appears as a single item.
     */
    public static List<Object> groupByDate(List<NotificationItem> notifications) {
        // 1. Separate unactioned JOIN_REQUEST (group by postId) from other notifications
        Map<Long, List<NotificationItem>> joinReqByPost = new LinkedHashMap<>();
        List<NotificationItem> others = new ArrayList<>();

        for (NotificationItem item : notifications) {
            if (item.getType() == NotificationItem.NotificationType.JOIN_REQUEST
                    && !item.isActioned()
                    && item.getRelatedPostId() != null) {
                joinReqByPost.computeIfAbsent(item.getRelatedPostId(), k -> new ArrayList<>())
                        .add(item);
            } else if (item.getType() != NotificationItem.NotificationType.JOIN_REQUEST) {
                others.add(item);
            }
            // Actioned JOIN_REQUEST: skip entirely (no longer relevant)
        }

        // 2. Build JoinRequestGroup for each post
        List<JoinRequestGroup> groups = new ArrayList<>();
        for (Map.Entry<Long, List<NotificationItem>> entry : joinReqByPost.entrySet()) {
            List<NotificationItem> reqs = entry.getValue();

            String latestCreatedAt = reqs.stream()
                    .map(NotificationItem::getCreatedAt)
                    .filter(s -> s != null && !s.isEmpty())
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            int unreadCount = (int) reqs.stream().filter(n -> !n.isRead()).count();

            // postTitle from the postTitle field (enriched by backend), fallback: parse from message
            String postTitle = null;
            for (NotificationItem req : reqs) {
                if (req.getPostTitle() != null && !req.getPostTitle().isEmpty()) {
                    postTitle = req.getPostTitle();
                    break;
                }
            }
            if (postTitle == null && !reqs.isEmpty()) {
                postTitle = extractPostTitle(reqs.get(0).getMessage());
            }

            groups.add(new JoinRequestGroup(entry.getKey(), postTitle, reqs.size(),
                    unreadCount, latestCreatedAt));
        }

        // 3. Merge groups + others into a sortable list, then build date-grouped result
        List<Object[]> sortable = new ArrayList<>(); // [item, createdAt String]
        for (JoinRequestGroup group : groups) {
            sortable.add(new Object[]{group, group.latestCreatedAt});
        }
        for (NotificationItem item : others) {
            sortable.add(new Object[]{item, item.getCreatedAt()});
        }

        // Sort by createdAt DESC (null goes to end)
        sortable.sort((a, b) -> {
            String ta = (String) a[1];
            String tb = (String) b[1];
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        // 4. Insert date headers
        List<Object> grouped = new ArrayList<>();
        String lastDateLabel = "";
        for (Object[] entry : sortable) {
            Object item = entry[0];
            String createdAt = (String) entry[1];
            String dateLabel = getDateLabelFromCreatedAt(createdAt);
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

    /** Extract post title from message: '... kèo "{title}" ...' */
    private static String extractPostTitle(String message) {
        if (message == null) return null;
        int start = message.indexOf('"');
        int end = message.lastIndexOf('"');
        if (start >= 0 && end > start) return message.substring(start + 1, end);
        return null;
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
        TextView tvMessage, tvSubtitle, tvTime, tvActioned;
        View viewUnreadDot;
        LinearLayout layoutActions;
        MaterialButton btnAccept, btnDecline;

        NotifViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivNotifAvatar);
            tvMessage = itemView.findViewById(R.id.tvNotifMessage);
            tvSubtitle = itemView.findViewById(R.id.tvNotifSubtitle);
            tvTime = itemView.findViewById(R.id.tvNotifTime);
            tvActioned = itemView.findViewById(R.id.tvNotifActioned);
            viewUnreadDot = itemView.findViewById(R.id.viewUnreadDot);
            layoutActions = itemView.findViewById(R.id.layoutNotifActions);
            btnAccept = itemView.findViewById(R.id.btnNotifAccept);
            btnDecline = itemView.findViewById(R.id.btnNotifDecline);
        }
    }
}
