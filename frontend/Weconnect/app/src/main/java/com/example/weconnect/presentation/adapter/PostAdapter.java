package com.example.weconnect.presentation.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.presentation.ui.ParticipantsActivity;
import com.example.weconnect.presentation.ui.PostDetailActivity;
import com.example.weconnect.presentation.ui.UserProfileActivity;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestoreChatRepository;
import com.example.weconnect.data.repository.FirestorePostRepository;
import com.example.weconnect.domain.model.Post;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PostAdapter — đã migrate hoàn toàn sang Firebase.
 * Xóa bỏ: RetrofitClient, PostApiService, ChatApiService, ReportApiService, FakePostRepository.
 */
public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private final Context context;
    private final List<Post> postList;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM", Locale.getDefault());
    private java.util.Set<String> viewerInterests;

    private final FirestorePostRepository postRepo;
    private final FirestoreChatRepository chatRepo;

    public PostAdapter(Context context, List<Post> postList) {
        this.context  = context;
        this.postList = postList;
        this.postRepo = new FirestorePostRepository();
        this.chatRepo = new FirestoreChatRepository();
    }

    public PostAdapter(Context context, List<Post> postList, java.util.Set<String> viewerInterests) {
        this(context, postList);
        this.viewerInterests = viewerInterests;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.tvUsername.setText(post.getUsername());
        holder.tvTime.setText(post.getTimeAgo());
        holder.tvContent.setText(post.getContent());
        holder.itemView.setOnClickListener(v -> openPostDetail(post));

        // Load avatar — nếu có URL dùng Glide (Firebase Storage URL), fallback drawable
        if (post.getAvatarUrl() != null && !post.getAvatarUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(context)
                .load(post.getAvatarUrl())
                .placeholder(R.drawable.ic_user_placeholder)
                .error(R.drawable.ic_user_placeholder)
                .circleCrop()
                .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(post.getAvatarResId());
        }
        holder.ivAvatar.setOnClickListener(v -> openUserProfile(post.getUsername(), post.getAuthorUid()));
        holder.tvUsername.setOnClickListener(v -> openUserProfile(post.getUsername(), post.getAuthorUid()));

        // Post image: Firebase Storage URL hoặc content:// URI cục bộ
        if (post.getPostImageUri() != null && !post.getPostImageUri().isEmpty()) {
            String imageUri = post.getPostImageUri();
            if (imageUri.startsWith("https://")) {
                holder.cvPostImage.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(context)
                    .load(imageUri)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .into(holder.ivPostImage);
            } else {
                try {
                    android.net.Uri uri = android.net.Uri.parse(imageUri);
                    java.io.InputStream is = context.getContentResolver().openInputStream(uri);
                    if (is != null) {
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                        is.close();
                        holder.cvPostImage.setVisibility(View.VISIBLE);
                        holder.ivPostImage.setImageBitmap(bmp);
                    } else {
                        holder.cvPostImage.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    holder.cvPostImage.setVisibility(View.GONE);
                }
            }
        } else if (post.getImageResId() != 0) {
            holder.cvPostImage.setVisibility(View.VISIBLE);
            holder.ivPostImage.setImageResource(post.getImageResId());
        } else {
            holder.cvPostImage.setVisibility(View.GONE);
        }

        // Tag
        if (post.getInterestTag() != null && !post.getInterestTag().isEmpty()) {
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTag.setText(post.getInterestTag());
        } else {
            holder.tvTag.setVisibility(View.GONE);
        }

        // Location
        if (post.getLocation() != null && !post.getLocation().isEmpty()) {
            holder.tvLocation.setVisibility(View.VISIBLE);
            holder.tvLocation.setText("Địa điểm: " + post.getLocation());
        } else {
            holder.tvLocation.setVisibility(View.GONE);
        }

        bindTimeRange(holder, post);
        bindExpiration(holder, post);

        holder.ivPostMenu.setOnClickListener(v -> showPostMenu(post, position));

        // Expired / archived
        if (post.isExpired() || post.isArchived()) {
            holder.layoutActiveButtons.setVisibility(View.GONE);
            holder.tvExpiredLabel.setVisibility(View.VISIBLE);
        } else {
            holder.layoutActiveButtons.setVisibility(View.VISIBLE);
            holder.tvExpiredLabel.setVisibility(View.GONE);
            bindActiveButtons(holder, post, position);
        }

        holder.btnViewMembers.setText("Thành viên: " + post.getMemberCount() + "/" + post.getMaxMembers());
        holder.btnViewMembers.setTextColor(0xFF000000);
        holder.btnViewMembers.setOnClickListener(v -> {
            Intent intent = new Intent(context, ParticipantsActivity.class);
            intent.putExtra("post_id", post.getId());
            intent.putExtra("post_author", post.getUsername());
            intent.putExtra("member_count", post.getMemberCount());
            intent.putExtra("max_members", post.getMaxMembers());
            context.startActivity(intent);
        });
    }

    // =========================================================================
    // Post menu
    // =========================================================================
    private void showPostMenu(Post post, int position) {
        String myUid = FirebaseManager.getCurrentUserId();
        boolean isOwn = myUid != null && myUid.equals(post.getAuthorUid());
        if (isOwn) showOwnPostMenu(post, position);
        else       showOtherPostMenu(post, position);
    }

    private void showOwnPostMenu(Post post, int position) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
            new com.google.android.material.bottomsheet.BottomSheetDialog(context);
        LinearLayout layout = buildMenuLayout();

        TextView tvEdit = createMenuItem("✏️  Chỉnh sửa bài viết");
        tvEdit.setOnClickListener(v -> { sheet.dismiss(); showEditPostDialog(post); });
        layout.addView(tvEdit);

        TextView tvDelete = createMenuItem("🗑️  Xoá bài viết");
        tvDelete.setTextColor(0xFFE53935);
        tvDelete.setOnClickListener(v -> { sheet.dismiss(); showDeleteConfirmation(post, position); });
        layout.addView(tvDelete);

        sheet.setContentView(layout);
        sheet.show();
    }

    private void showEditPostDialog(Post post) {
        Intent intent = new Intent(context, com.example.weconnect.presentation.ui.CreatePostActivity.class);
        intent.putExtra("edit_mode", true);
        intent.putExtra("edit_post_id", post.getId());
        intent.putExtra("edit_content", post.getContent());
        intent.putExtra("edit_tag", post.getInterestTag());
        intent.putExtra("edit_location", post.getLocation());
        intent.putExtra("edit_max_members", post.getMaxMembers());
        intent.putExtra("edit_end_time", post.getEndTimeMillis());
        if (post.getPostImageUri() != null) intent.putExtra("edit_image_uri", post.getPostImageUri());
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).startActivityForResult(intent, 2001);
        }
    }

    private void showDeleteConfirmation(Post post, int position) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Xoá bài viết")
            .setMessage("Bạn có chắc chắn muốn xoá bài viết này không?")
            .setPositiveButton("Xác nhận xoá", (dialog, which) -> {
                String currentUid = com.example.weconnect.data.repository.FirebaseManager.getCurrentUserId();
                postRepo.deletePost(post.getId(), currentUid, new FirestorePostRepository.ActionCallback() {
                    @Override public void onSuccess(String id) {
                        postList.remove(position);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, postList.size());
                        Toast.makeText(context, "Đã xoá bài viết", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String err) {
                        // Remove locally anyway to keep UX smooth
                        postList.remove(position);
                        notifyItemRemoved(position);
                        Toast.makeText(context, "Đã xoá bài viết", Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Huỷ", null)
            .show();
    }

    private void showOtherPostMenu(Post post, int position) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
            new com.google.android.material.bottomsheet.BottomSheetDialog(context);
        LinearLayout layout = buildMenuLayout();

        TextView tvHide = createMenuItem("👁‍🗨  Ẩn bài viết");
        tvHide.setOnClickListener(v -> { sheet.dismiss(); hidePost(position); });
        layout.addView(tvHide);

        TextView tvReport = createMenuItem("🚩  Báo cáo bài viết");
        tvReport.setOnClickListener(v -> { sheet.dismiss(); showReportDialog(post, position); });
        layout.addView(tvReport);

        sheet.setContentView(layout);
        sheet.show();
    }

    private void hidePost(int position) {
        postList.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, postList.size());
        Toast.makeText(context, "Đã ẩn bài viết", Toast.LENGTH_SHORT).show();
    }

    private void showReportDialog(Post post, int position) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
            new com.google.android.material.bottomsheet.BottomSheetDialog(context);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(context.getResources().getColor(R.color.soft_beige, null));
        root.setPadding(0, 0, 0, 48);

        TextView header = new TextView(context);
        header.setText("🚩 Báo cáo bài viết");
        header.setTextSize(20);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(context.getResources().getColor(R.color.primary_pink, null));
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 40, 0, 16);
        root.addView(header);

        RadioGroup rg = new RadioGroup(context);
        rg.setPadding(64, 24, 64, 24);
        String[] reasons = {"Nội dung thô tục", "Vi phạm quy định cộng đồng",
            "Spam / Quảng cáo", "Thông tin sai lệch", "Quấy rối / Bắt nạt", "Khác"};
        for (int i = 0; i < reasons.length; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setText(reasons[i]);
            rb.setId(i);
            rb.setPadding(24, 20, 24, 20);
            rb.setTextSize(15);
            rg.addView(rb);
        }
        root.addView(rg);

        EditText etNote = new EditText(context);
        etNote.setHint("Mô tả chi tiết (không bắt buộc)");
        etNote.setMinLines(2);
        etNote.setPadding(64, 28, 64, 28);
        etNote.setBackground(null);
        root.addView(etNote);

        com.google.android.material.button.MaterialButton btnSend =
            new com.google.android.material.button.MaterialButton(context);
        btnSend.setText("Gửi báo cáo");
        btnSend.setAllCaps(false);
        btnSend.setCornerRadius(72);
        btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            context.getResources().getColor(R.color.primary_pink, null)));
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnP.setMargins(48, 32, 48, 0);
        btnSend.setLayoutParams(btnP);
        btnSend.setOnClickListener(v -> {
            if (rg.getCheckedRadioButtonId() == -1) {
                Toast.makeText(context, "Vui lòng chọn lý do báo cáo", Toast.LENGTH_SHORT).show();
                return;
            }
            sheet.dismiss();
            // Lưu report vào Firestore reports/{postId}
            String selectedReason = reasons[rg.getCheckedRadioButtonId()];
            String description    = etNote.getText().toString().trim();
            postRepo.reportPost(post.getId(), FirebaseManager.getCurrentUserId(),
                selectedReason, description, new FirestorePostRepository.ActionCallback() {
                    @Override public void onSuccess(String id) {}
                    @Override public void onError(String err) {}
                });
            Toast.makeText(context, "✅ Đã gửi báo cáo vi phạm. Cảm ơn bạn!", Toast.LENGTH_LONG).show();
            hidePost(position);
        });
        root.addView(btnSend);

        android.widget.ScrollView sv = new android.widget.ScrollView(context);
        sv.addView(root);
        sheet.setContentView(sv);
        sheet.show();
    }

    // =========================================================================
    // Active buttons (Join / Chat)
    // =========================================================================
    private void bindActiveButtons(PostViewHolder holder, Post post, int position) {
        String myUid = FirebaseManager.getCurrentUserId();
        boolean isOwn = myUid != null && myUid.equals(post.getAuthorUid());

        if (isOwn) {
            holder.btnJoinGroup.setVisibility(View.GONE);
            android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) (48 * holder.itemView.getResources().getDisplayMetrics().density));
            p.weight = 0;
            holder.btnViewMembers.setLayoutParams(p);
            holder.btnViewMembers.setPadding(48, 0, 48, 0);
        } else {
            boolean tagMatch = true;
            if (viewerInterests != null && !viewerInterests.isEmpty()) {
                String tag = post.getInterestTag();
                tagMatch = tag != null && viewerInterests.contains(tag.trim().toLowerCase());
            }
            if (!tagMatch) {
                holder.btnJoinGroup.setVisibility(View.GONE);
                android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    (int) (48 * holder.itemView.getResources().getDisplayMetrics().density));
                p.weight = 0;
                holder.btnViewMembers.setLayoutParams(p);
                holder.btnViewMembers.setPadding(48, 0, 48, 0);
            } else {
                holder.btnJoinGroup.setVisibility(View.VISIBLE);
                android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(
                    0, (int) (48 * holder.itemView.getResources().getDisplayMetrics().density));
                p.weight = 1;
                p.setMarginStart((int) (6 * holder.itemView.getResources().getDisplayMetrics().density));
                holder.btnViewMembers.setLayoutParams(p);
                holder.btnViewMembers.setPadding(0, 0, 0, 0);

                if (post.isJoined()) {
                    holder.btnJoinGroup.setText("💬 Mở đoạn chat");
                    holder.btnJoinGroup.setEnabled(true);
                    holder.btnJoinGroup.setAlpha(1f);
                    holder.btnJoinGroup.setOnClickListener(v -> openChatRoom(post));
                } else if (post.isPendingApproval()) {
                    holder.btnJoinGroup.setText("⏳ Đang chờ duyệt");
                    holder.btnJoinGroup.setEnabled(false);
                    holder.btnJoinGroup.setAlpha(0.6f);
                    holder.btnJoinGroup.setOnClickListener(null);
                } else {
                    holder.btnJoinGroup.setText("Tham gia");
                    holder.btnJoinGroup.setEnabled(true);
                    holder.btnJoinGroup.setAlpha(1f);
                    holder.btnJoinGroup.setOnClickListener(v -> joinPost(post, holder));
                }
            }
        }
    }

    /** Gửi yêu cầu tham gia qua Firestore */
    private void joinPost(Post post, PostViewHolder holder) {
        String myUid  = FirebaseManager.getCurrentUserId();
        String myName = FirebaseManager.getUserName(context);
        if (myUid == null) return;

        postRepo.joinPost(post.getId(), myUid, myName, new FirestorePostRepository.ActionCallback() {
            @Override public void onSuccess(String id) {
                post.setPendingApproval(true);
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        holder.btnJoinGroup.setText("⏳ Đang chờ duyệt");
                        holder.btnJoinGroup.setEnabled(false);
                        holder.btnJoinGroup.setAlpha(0.6f);
                        Toast.makeText(context, "Đã gửi yêu cầu tham gia!", Toast.LENGTH_SHORT).show();
                    });
                }
            }
            @Override public void onError(String err) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() ->
                        Toast.makeText(context, "Lỗi khi gửi yêu cầu. Thử lại!", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    /** Mở phòng chat của bài viết qua Firestore */
    private void openChatRoom(Post post) {
        chatRepo.getRoomByPostId(post.getId(), new FirestoreChatRepository.RoomCallback() {
            @Override public void onSuccess(java.util.Map<String, Object> room) {
                String roomId = room.get("id") != null ? room.get("id").toString() : null;
                if (roomId != null) {
                    Intent intent = new Intent(context, com.example.weconnect.presentation.ui.ConversationActivity.class);
                    intent.putExtra("room_id", roomId);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "Lỗi phòng chat", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onError(String err) {
                Toast.makeText(context, "Không tìm thấy phòng chat cho bài viết này", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // =========================================================================
    // Time helpers
    // =========================================================================
    private void bindTimeRange(PostViewHolder holder, Post post) {
        long start = post.getStartTimeMillis(), end = post.getEndTimeMillis();
        if (start > 0 && end > 0) {
            holder.layoutTimeRange.setVisibility(View.VISIBLE);
            holder.tvDateRange.setText("📅 " + DATE_FORMAT.format(new Date(start)) + " - " + DATE_FORMAT.format(new Date(end)));
            long remaining = end - System.currentTimeMillis();
            if (remaining > 0 && remaining <= 24L * 60 * 60 * 1000) {
                holder.tvCountdown.setVisibility(View.VISIBLE);
                holder.tvCountdown.setText("⏰ " + formatCountdown(remaining));
            } else if (remaining <= 0) {
                holder.tvCountdown.setVisibility(View.VISIBLE);
                holder.tvCountdown.setText("Hết hạn");
            } else {
                holder.tvCountdown.setVisibility(View.GONE);
            }
        } else {
            holder.layoutTimeRange.setVisibility(View.GONE);
        }
    }

    private void bindExpiration(PostViewHolder holder, Post post) {
        long s = post.getStartTimeMillis(), e = post.getEndTimeMillis();
        if (s > 0 && e > 0 && e > s) {
            holder.tvExpirationHours.setVisibility(View.VISIBLE);
            long mins = (e - s) / 60000L;
            long h = mins / 60, m = mins % 60;
            if (h >= 24 && h % 24 == 0) holder.tvExpirationHours.setText("⏳ Thời hạn: " + (h / 24) + " ngày (" + h + " giờ)");
            else if (h > 0 && m > 0)    holder.tvExpirationHours.setText("⏳ Thời hạn: " + h + " giờ " + m + " phút");
            else if (h > 0)             holder.tvExpirationHours.setText("⏳ Thời hạn: " + h + " giờ");
            else                        holder.tvExpirationHours.setText("⏳ Thời hạn: " + m + " phút");
        } else if (post.getExpirationHours() > 0) {
            holder.tvExpirationHours.setVisibility(View.VISIBLE);
            holder.tvExpirationHours.setText("⏳ Thời hạn: " + post.getExpirationHours() + " giờ");
        } else {
            holder.tvExpirationHours.setVisibility(View.GONE);
        }
    }

    private String formatCountdown(long millis) {
        long h = millis / (3600000L), m = (millis % 3600000L) / 60000L;
        if (h > 0 && m > 0) return "Còn " + h + " giờ " + m + " phút";
        if (h > 0)           return "Còn " + h + " giờ";
        return "Còn " + m + " phút";
    }

    @Override public int getItemCount() { return postList.size(); }

    private void openPostDetail(Post post) {
        Intent intent = new Intent(context, PostDetailActivity.class);
        intent.putExtra("post", post);
        context.startActivity(intent);
    }

    private void openUserProfile(String username, String authorUid) {
        String myUid = FirebaseManager.getCurrentUserId();
        Intent intent = new Intent(context, UserProfileActivity.class);
        intent.putExtra("username", username);
        intent.putExtra("user_uid", authorUid != null ? authorUid : "");
        if (myUid != null && !myUid.equals(authorUid)) {
            intent.putExtra("view_other", true);
        }
        context.startActivity(intent);
    }

    private LinearLayout buildMenuLayout() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(context.getResources().getColor(R.color.soft_beige, null));
        layout.setPadding(0, 32, 0, 32);

        TextView header = new TextView(context);
        header.setText("Tuỳ chọn bài viết");
        header.setTextSize(18);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(context.getResources().getColor(R.color.primary_pink, null));
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 16, 0, 24);
        layout.addView(header);

        View div = new View(context);
        div.setBackgroundColor(0xFFE8E4DE);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        layout.addView(div);
        return layout;
    }

    private TextView createMenuItem(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(context.getResources().getColor(R.color.text_primary, null));
        tv.setPadding(64, 40, 64, 40);
        tv.setBackgroundResource(android.R.drawable.list_selector_background);
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================
    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivPostImage, ivPostMenu;
        View cvPostImage;
        TextView tvUsername, tvTime, tvContent;
        TextView btnJoinGroup, btnViewMembers;
        TextView tvTag, tvLocation;
        LinearLayout layoutTimeRange, layoutActiveButtons;
        TextView tvDateRange, tvCountdown, tvExpiredLabel, tvExpirationHours;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar        = itemView.findViewById(R.id.post_item_avatar);
            tvUsername      = itemView.findViewById(R.id.post_item_username);
            tvTime          = itemView.findViewById(R.id.post_item_time);
            tvContent       = itemView.findViewById(R.id.post_item_content);
            cvPostImage     = itemView.findViewById(R.id.cvPostImage);
            ivPostImage     = itemView.findViewById(R.id.ivPostImage);
            btnJoinGroup    = itemView.findViewById(R.id.btnJoinGroup);
            btnViewMembers  = itemView.findViewById(R.id.btnViewMembers);
            tvTag           = itemView.findViewById(R.id.post_item_tag);
            tvLocation      = itemView.findViewById(R.id.post_item_location);
            layoutTimeRange = itemView.findViewById(R.id.layoutTimeRange);
            tvDateRange     = itemView.findViewById(R.id.post_item_date_range);
            tvCountdown     = itemView.findViewById(R.id.post_item_countdown);
            layoutActiveButtons = itemView.findViewById(R.id.layoutActiveButtons);
            tvExpiredLabel  = itemView.findViewById(R.id.tvExpiredLabel);
            tvExpirationHours = itemView.findViewById(R.id.tvExpirationHours);
            ivPostMenu      = itemView.findViewById(R.id.ivPostMenu);
        }
    }
}
