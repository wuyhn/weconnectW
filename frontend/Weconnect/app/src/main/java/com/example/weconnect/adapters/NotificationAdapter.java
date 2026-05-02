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
import com.example.weconnect.activities.PostDetailActivity;
import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.api.FirebaseFriendService;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestoreNotificationRepository;
import com.example.weconnect.api.FirestorePostRepository;
import com.example.weconnect.models.NotificationItem;
import com.example.weconnect.models.Post;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NotificationAdapter — đã migrate sang Firebase.
 * Xóa: RetrofitClient, PostApiService, FriendApiService, NotificationApiService, FakeRepositories.
 * Dùng: FirestoreNotificationRepository, FirestorePostRepository, FirebaseFriendService.
 */
public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER    = 0;
    private static final int TYPE_ITEM_REAL = 1;

    private final Context context;
    private final List<Object> items; // String (header) hoặc NotificationItem

    private final FirestoreNotificationRepository notifRepo;
    private final FirestorePostRepository postRepo;
    private final FirebaseFriendService friendService;

    public NotificationAdapter(Context context, List<Object> items) {
        this.context       = context;
        this.items         = items;
        this.notifRepo     = new FirestoreNotificationRepository();
        this.postRepo      = new FirestorePostRepository();
        this.friendService = new FirebaseFriendService();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM_REAL;
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
        } else if (holder instanceof NotifViewHolder && items.get(position) instanceof NotificationItem) {
            bindRealNotification((NotifViewHolder) holder, (NotificationItem) items.get(position));
        }
    }

    private void bindRealNotification(NotifViewHolder holder, NotificationItem item) {
        holder.tvMessage.setText(item.getMessage());
        holder.tvTime.setText(formatCreatedAt(item.getCreatedAt()));

        // Avatar — placeholder (avatarUrl từ Firestore nếu có thì load qua Glide)
        String avatarUrl = item.getSenderAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(context)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_user_placeholder)
                .error(R.drawable.ic_user_placeholder)
                .circleCrop()
                .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

        boolean isActionable = (NotificationItem.NotificationType.JOIN_REQUEST.name().equals(item.getType())
            || NotificationItem.NotificationType.FRIEND_REQUEST_RECEIVED.name().equals(item.getType()));

        if (isActionable && !item.isActioned()) {
            if (NotificationItem.NotificationType.JOIN_REQUEST.name().equals(item.getType())
                    && item.getRelatedPostId() != null) {
                // Kiểm tra trạng thái bài viết từ Firestore
                holder.layoutActions.setVisibility(View.GONE);
                holder.tvActioned.setVisibility(View.GONE);

                postRepo.getPost(String.valueOf(item.getRelatedPostId()),
                    new FirestorePostRepository.PostCallback() {
                        @Override public void onSuccess(Map<String, Object> postData) {
                            boolean expired = Boolean.TRUE.equals(postData.get("expired"))
                                || Boolean.TRUE.equals(postData.get("archived"));
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> {
                                    if (expired) {
                                        holder.layoutActions.setVisibility(View.GONE);
                                        holder.tvActioned.setVisibility(View.VISIBLE);
                                        holder.tvActioned.setText("⏰ Đã hết hạn");
                                        markActioned(item, holder, null);
                                    } else {
                                        showJoinRequestActions(holder, item);
                                    }
                                });
                            }
                        }
                        @Override public void onError(String err) {
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() ->
                                    showJoinRequestActions(holder, item)
                                );
                            }
                        }
                    });
            } else if (NotificationItem.NotificationType.FRIEND_REQUEST_RECEIVED.name().equals(item.getType())
                    && item.getRelatedUserId() != null) {
                holder.layoutActions.setVisibility(View.VISIBLE);
                holder.tvActioned.setVisibility(View.GONE);
                holder.btnAccept.setText("Chấp nhận");
                holder.btnDecline.setText("Từ chối");

                String relUid = String.valueOf(item.getRelatedUserId());
                holder.btnAccept.setOnClickListener(v ->
                    friendService.acceptFriendRequest(relUid, com.example.weconnect.api.FirebaseManager.getCurrentUserId(), new FirebaseFriendService.ActionCallback() {
                        @Override public void onSuccess(String msg) {
                            markActioned(item, holder, "Đã chấp nhận lời mời kết bạn");
                        }
                        @Override public void onError(String err) {
                            Toast.makeText(context, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
                        }
                    })
                );
                holder.btnDecline.setOnClickListener(v ->
                    friendService.declineFriendRequest(relUid, com.example.weconnect.api.FirebaseManager.getCurrentUserId(), new FirebaseFriendService.ActionCallback() {
                        @Override public void onSuccess(String msg) {
                            markActioned(item, holder, "Đã từ chối lời mời kết bạn");
                        }
                        @Override public void onError(String err) {
                            Toast.makeText(context, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
                        }
                    })
                );
            } else {
                holder.layoutActions.setVisibility(View.GONE);
                holder.tvActioned.setVisibility(View.GONE);
            }
        } else if (isActionable && item.isActioned()) {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.VISIBLE);
            holder.tvActioned.setText("✅ Đã xử lý");
        } else {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.GONE);
        }

        // Click item → mark read + navigate
        holder.itemView.setOnClickListener(v -> {
            if (!item.isRead()) {
                item.setRead(true);
                holder.viewUnreadDot.setVisibility(View.GONE);
                holder.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                notifRepo.markAsRead(item.getId(), new FirestoreNotificationRepository.ActionCallback() {
                    @Override public void onSuccess(String msg) {}
                    @Override public void onError(String err) {}
                });
            }
            if (item.getRelatedPostId() != null) {
                navigateToPost(item);
            } else if (item.getRelatedUsername() != null && !item.getRelatedUsername().isEmpty()) {
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("username", item.getRelatedUsername());
                intent.putExtra("view_other", true);
                if (item.getRelatedUserId() != null) {
                    intent.putExtra("user_uid", String.valueOf(item.getRelatedUserId()));
                }
                context.startActivity(intent);
            }
        });

        // Unread dot
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
                intent.putExtra("user_uid", String.valueOf(item.getRelatedUserId()));
            }
            context.startActivity(intent);
        });
    }

    private void approveJoinRequest(NotificationItem item, NotifViewHolder holder) {
        if (item.getRelatedPostId() == null || item.getRelatedUserId() == null) {
            Toast.makeText(context, "Thiếu thông tin để duyệt", Toast.LENGTH_SHORT).show();
            return;
        }
        String postId  = String.valueOf(item.getRelatedPostId());
        String userUid = String.valueOf(item.getRelatedUserId());

        postRepo.approveMember(postId, userUid, com.example.weconnect.api.FirebaseManager.getCurrentUserId(), new FirestorePostRepository.ActionCallback() {
            @Override public void onSuccess(String id) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        markActioned(item, holder, "✅ Bạn đã duyệt " + item.getRelatedUsername());
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                            .setTitle("Đã duyệt!")
                            .setMessage("Bạn đã duyệt " + item.getRelatedUsername() + " tham gia hoạt động.")
                            .setPositiveButton("OK", null).show();
                    });
                }
            }
            @Override public void onError(String err) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() ->
                        Toast.makeText(context, "Lỗi khi duyệt: " + err, Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private void markActioned(NotificationItem item, NotifViewHolder holder, String message) {
        item.setActioned(true);
        item.setRead(true);
        notifRepo.markAsActioned(item.getId(), new FirestoreNotificationRepository.ActionCallback() {
            @Override public void onSuccess(String msg) {}
            @Override public void onError(String err) {}
        });
        notifyItemChanged(holder.getAdapterPosition());
        if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private void navigateToPost(NotificationItem item) {
        String postId = String.valueOf(item.getRelatedPostId());
        postRepo.getPost(postId, new FirestorePostRepository.PostCallback() {
            @Override public void onSuccess(Map<String, Object> postData) {
                Post post = mapToPost(postData);
                if (NotificationItem.NotificationType.JOIN_APPROVED.name().equals(item.getType())) {
                    post.setJoined(true);
                }
                Intent intent = new Intent(context, PostDetailActivity.class);
                intent.putExtra("post", post);
                if (NotificationItem.NotificationType.JOIN_REQUEST.name().equals(item.getType()) && !item.isActioned()) {
                    intent.putExtra("show_pending_actions", true);
                    if (item.getRelatedUserId() != null) {
                        intent.putExtra("pending_user_uid", String.valueOf(item.getRelatedUserId()));
                    }
                    intent.putExtra("pending_username", item.getRelatedUsername());
                }
                context.startActivity(intent);
            }
            @Override public void onError(String err) {
                Toast.makeText(context, "Không thể tải bài viết", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Post mapToPost(Map<String, Object> data) {
        Post post = new Post();
        if (data.get("id") != null)       post.setId(data.get("id").toString());
        if (data.get("content") != null)  post.setContent(data.get("content").toString());
        if (data.get("authorName") != null) post.setUsername(data.get("authorName").toString());
        if (data.get("authorUid") != null)  post.setAuthorUid(data.get("authorUid").toString());
        if (data.get("interestTag") != null) post.setInterestTag(data.get("interestTag").toString());
        if (data.get("location") != null)   post.setLocation(data.get("location").toString());
        return post;
    }

    @Override public int getItemCount() { return items.size(); }

    public void markAllRead() {
        for (Object item : items) {
            if (item instanceof NotificationItem) ((NotificationItem) item).setRead(true);
        }
        notifyDataSetChanged();
    }

    public static List<Object> groupByDate(List<NotificationItem> notifications) {
        List<Object> grouped = new ArrayList<>();
        String last = "";
        for (NotificationItem item : notifications) {
            String label = getDateLabelFromCreatedAt(item.getCreatedAt());
            if (!label.equals(last)) { grouped.add(label); last = label; }
            grouped.add(item);
        }
        return grouped;
    }

    private static String getDateLabelFromCreatedAt(String createdAt) {
        if (createdAt == null) return "Khác";
        try {
            java.time.LocalDateTime dt = java.time.LocalDateTime.parse(createdAt);
            java.time.LocalDate d = dt.toLocalDate(), today = java.time.LocalDate.now();
            if (d.equals(today)) return "Hôm nay";
            if (d.equals(today.minusDays(1))) return "Hôm qua";
            return d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) { return "Khác"; }
    }

    private static String getDateLabel(long ts) {
        Calendar c = Calendar.getInstance(), today = Calendar.getInstance(),
            yesterday = Calendar.getInstance();
        c.setTimeInMillis(ts);
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(c, today)) return "Hôm nay";
        if (sameDay(c, yesterday)) return "Hôm qua";
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(ts));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private String formatCreatedAt(String createdAt) {
        if (createdAt == null) return "";
        try {
            java.time.LocalDateTime dt = java.time.LocalDateTime.parse(createdAt);
            java.time.Duration dur = java.time.Duration.between(dt, java.time.LocalDateTime.now());
            long mins = dur.toMinutes(), hours = dur.toHours(), days = dur.toDays();
            if (mins < 1) return "Vừa xong";
            if (mins < 60) return mins + " phút trước";
            if (hours < 24) return hours + " giờ trước";
            if (days < 7) return days + " ngày trước";
            return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) { return createdAt; }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(TextView tv) { super(tv); tvHeader = tv; }
    }

    static class NotifViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvMessage, tvTime, tvActioned;
        View viewUnreadDot;
        LinearLayout layoutActions;
        MaterialButton btnAccept, btnDecline;

        NotifViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar      = itemView.findViewById(R.id.ivNotifAvatar);
            tvMessage     = itemView.findViewById(R.id.tvNotifMessage);
            tvTime        = itemView.findViewById(R.id.tvNotifTime);
            tvActioned    = itemView.findViewById(R.id.tvNotifActioned);
            viewUnreadDot = itemView.findViewById(R.id.viewUnreadDot);
            layoutActions = itemView.findViewById(R.id.layoutNotifActions);
            btnAccept     = itemView.findViewById(R.id.btnNotifAccept);
            btnDecline    = itemView.findViewById(R.id.btnNotifDecline);
        }
    }
}
