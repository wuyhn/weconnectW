package com.example.weconnect.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.net.Uri;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.activities.ParticipantsActivity;
import com.example.weconnect.activities.PostDetailActivity;
import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.models.Post;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private final Context context;
    private final List<Post> postList;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM", Locale.getDefault());
    private final String currentUsername;
    private java.util.Set<String> viewerInterests;

    public PostAdapter(Context context, List<Post> postList) {
        this.context = context;
        this.postList = postList;
        this.currentUsername = FakePostRepository.getInstance().getCurrentUsername();
    }

    /**
     * Constructor với viewer interests để kiểm soát nút tham gia.
     * Chỉ posts có tag trùng với interests của viewer mới hiện nút "Tham gia".
     */
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

        // Load avatar: ưu tiên global cache (realtime WebSocket) → own cache → post data
        long myId = com.example.weconnect.api.RetrofitClient.getUserId(context);
        String avatarUrl = post.getAvatarUrl();
        if (post.getAuthorId() > 0) {
            String globalCached = com.example.weconnect.api.RetrofitClient.getCachedAvatarForUser(post.getAuthorId());
            if (globalCached != null && !globalCached.isEmpty()) {
                avatarUrl = globalCached;
            } else if (myId > 0 && post.getAuthorId() == myId) {
                String myCached = com.example.weconnect.api.RetrofitClient.getAvatarUrl(context);
                if (myCached != null && !myCached.isEmpty()) avatarUrl = myCached;
            }
        }
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (avatarUrl.startsWith("/")) {
                avatarUrl = com.example.weconnect.api.RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
            }
            com.bumptech.glide.Glide.with(context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(post.getAvatarResId());
        }
        holder.ivAvatar.setOnClickListener(v -> openUserProfile(post.getUsername(), post.getAuthorId()));
        holder.tvUsername.setOnClickListener(v -> openUserProfile(post.getUsername(), post.getAuthorId()));

        // Post image: URI từ thư viện hoặc server URL hoặc resource id
        if (post.getPostImageUri() != null && !post.getPostImageUri().isEmpty()) {
            String imageUriStr = post.getPostImageUri();
            if (imageUriStr.startsWith("/uploads/") || imageUriStr.startsWith("http")) {
                // Server-hosted image — dùng Glide để tránh ViewHolder recycling bugs
                String fullUrl = imageUriStr.startsWith("http")
                        ? imageUriStr
                        : com.example.weconnect.api.RetrofitClient.getBaseUrl() + imageUriStr.substring(1);
                holder.cvPostImage.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(context)
                        .load(fullUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .into(holder.ivPostImage);
            } else {
                // Local content:// URI — load via ContentResolver
                try {
                    Uri imageUri = Uri.parse(imageUriStr);
                    InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
                    if (inputStream != null) {
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        inputStream.close();
                        holder.cvPostImage.setVisibility(View.VISIBLE);
                        holder.ivPostImage.setImageBitmap(bitmap);
                    } else {
                        holder.cvPostImage.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    // URI hết quyền truy cập → ẩn ảnh thay vì crash
                    holder.cvPostImage.setVisibility(View.GONE);
                }
            }
        } else if (post.getImageResId() != 0) {
            holder.cvPostImage.setVisibility(View.VISIBLE);
            holder.ivPostImage.setImageResource(post.getImageResId());
        } else {
            holder.cvPostImage.setVisibility(View.GONE);
        }

        if (post.getInterestTag() != null && !post.getInterestTag().isEmpty()) {
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTag.setText(post.getInterestTag());
        } else {
            holder.tvTag.setVisibility(View.GONE);
        }

        if (post.getLocation() != null && !post.getLocation().isEmpty()) {
            holder.tvLocation.setVisibility(View.VISIBLE);
            holder.tvLocation.setText("Địa điểm: " + post.getLocation());
        } else {
            holder.tvLocation.setVisibility(View.GONE);
        }

        // Time Range
        bindTimeRange(holder, post);

        // Expiration: tính từ startTime → endTime thực tế
        long startMs = post.getStartTimeMillis();
        long endMs = post.getEndTimeMillis();
        if (startMs > 0 && endMs > 0 && endMs > startMs) {
            holder.tvExpirationHours.setVisibility(View.VISIBLE);
            long durationMinutes = (endMs - startMs) / (60L * 1000L);
            long dHours = durationMinutes / 60;
            long dMins = durationMinutes % 60;
            if (dHours >= 24 && dHours % 24 == 0) {
                holder.tvExpirationHours.setText("⏳ Thời hạn: " + (dHours / 24) + " ngày (" + dHours + " giờ)");
            } else if (dHours > 0 && dMins > 0) {
                holder.tvExpirationHours.setText("⏳ Thời hạn: " + dHours + " giờ " + dMins + " phút");
            } else if (dHours > 0) {
                holder.tvExpirationHours.setText("⏳ Thời hạn: " + dHours + " giờ");
            } else {
                holder.tvExpirationHours.setText("⏳ Thời hạn: " + dMins + " phút");
            }
        } else if (post.getExpirationHours() > 0) {
            holder.tvExpirationHours.setVisibility(View.VISIBLE);
            int hours = post.getExpirationHours();
            holder.tvExpirationHours.setText("⏳ Thời hạn: " + hours + " giờ");
        } else {
            holder.tvExpirationHours.setVisibility(View.GONE);
        }

        // Post menu (⋯)
        holder.ivPostMenu.setOnClickListener(v -> showPostMenu(post, position));

        // Expired state
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
            intent.putExtra("author_user_id", post.getAuthorId());
            context.startActivity(intent);
        });
    }

    private void showPostMenu(Post post, int position) {
        boolean isOwnPost = currentUsername.equalsIgnoreCase(post.getUsername());

        if (isOwnPost) {
            showOwnPostMenu(post, position);
        } else {
            showOtherPostMenu(post, position);
        }
    }

    private void showOwnPostMenu(Post post, int position) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(context);
        sheet.getBehavior().setSkipCollapsed(true);

        LinearLayout root = buildIosRoot();
        LinearLayout group1 = buildIosGroup();
        addIosHeader(group1, "Tuỳ chọn bài viết");
        addIosSep(group1);
        addIosRow(group1, "Chỉnh sửa bài viết", 0xFF1C1C1E, v -> { sheet.dismiss(); showEditPostDialog(post, position); });
        addIosSep(group1);
        addIosRow(group1, "Hủy hoạt động", 0xFFFF3B30, v -> { sheet.dismiss(); showCancelActivityConfirmation(post, position); });
        root.addView(group1, matchW());
        addGroupGap(root);
        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Huỷ", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());
        sheet.setContentView(root);
        makeSheetTransparent(root);
        sheet.show();
    }

    private void showEditPostDialog(Post post, int position) {
        Intent intent = new Intent(context, com.example.weconnect.activities.CreatePostActivity.class);
        intent.putExtra("edit_mode", true);
        intent.putExtra("edit_post_id", Long.parseLong(post.getId()));
        intent.putExtra("edit_content", post.getContent());
        intent.putExtra("edit_tag", post.getInterestTag());
        intent.putExtra("edit_location", post.getLocation());
        intent.putExtra("edit_max_members", post.getMaxMembers());
        intent.putExtra("edit_end_time", post.getEndTimeMillis());
        if (post.getActivityEndTimeStr() != null && !post.getActivityEndTimeStr().isEmpty()) {
            java.text.SimpleDateFormat isoFmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            intent.putExtra("edit_activity_start_iso", isoFmt.format(new java.util.Date(post.getStartTimeMillis())));
            intent.putExtra("edit_activity_end_iso", post.getActivityEndTimeStr());
        }
        if (post.getActivityTimeType() != null) {
            intent.putExtra("edit_activity_time_type", post.getActivityTimeType());
        }
        if (post.getPostImageUri() != null) {
            intent.putExtra("edit_image_uri", post.getPostImageUri());
        }
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).startActivityForResult(intent, 2001);
        }
    }

    private void showCancelActivityConfirmation(Post post, int position) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Hủy hoạt động?")
                .setMessage("Khi hủy hoạt động, bài viết và nhóm chat của hoạt động này sẽ không còn khả dụng với tất cả thành viên.")
                .setPositiveButton("Xác nhận hủy", (dialog, which) -> doCancelActivity(post, position))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void doCancelActivity(Post post, int position) {
        long postId;
        try {
            postId = Long.parseLong(post.getId());
        } catch (Exception e) {
            Toast.makeText(context, "Lỗi ID bài viết", Toast.LENGTH_SHORT).show();
            return;
        }
        com.example.weconnect.api.RetrofitClient.loadToken(context);
        com.example.weconnect.api.PostApiService postApi =
                com.example.weconnect.api.RetrofitClient.getClient()
                        .create(com.example.weconnect.api.PostApiService.class);
        postApi.cancelActivity(postId).enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<Void>>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<Void>> call,
                                   retrofit2.Response<com.example.weconnect.models.ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    postList.remove(position);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, postList.size());
                    Toast.makeText(context, "Đã hủy hoạt động", Toast.LENGTH_SHORT).show();
                } else {
                    String errMsg = "Không thể hủy hoạt động";
                    try {
                        if (response.errorBody() != null) {
                            org.json.JSONObject json = new org.json.JSONObject(response.errorBody().string());
                            if (json.has("message")) errMsg = json.getString("message");
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(context, "Lỗi kết nối. Thử lại!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOtherPostMenu(Post post, int position) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(context);
        sheet.getBehavior().setSkipCollapsed(true);

        LinearLayout root = buildIosRoot();
        LinearLayout group1 = buildIosGroup();
        addIosRow(group1, "Ẩn bài viết", 0xFF1C1C1E, v -> { sheet.dismiss(); hidePost(position); });
        addIosSep(group1);
        addIosRow(group1, "Báo cáo bài viết", 0xFFFF3B30, v -> { sheet.dismiss(); showReportDialog(post, position); });
        root.addView(group1, matchW());
        addGroupGap(root);
        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Huỷ", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());
        sheet.setContentView(root);
        makeSheetTransparent(root);
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
        sheet.getBehavior().setSkipCollapsed(true);

        String[] reasons = {
                "Nội dung thô tục",
                "Vi phạm quy định cộng đồng",
                "Spam / Quảng cáo",
                "Thông tin sai lệch",
                "Quấy rối / Bắt nạt",
                "Khác"
        };
        int[] selectedIndex = { -1 };
        TextView[] reasonRows = new TextView[reasons.length];

        LinearLayout root = buildIosRoot();

        // Group 1: header + reason rows
        LinearLayout group1 = buildIosGroup();
        addIosHeader(group1, "Báo cáo bài viết");
        for (int i = 0; i < reasons.length; i++) {
            addIosSep(group1);
            final int idx = i;
            TextView tv = new TextView(context);
            tv.setText(reasons[i]);
            tv.setTextSize(17);
            tv.setTextColor(0xFF1C1C1E);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
            tv.setClickable(true);
            tv.setFocusable(true);
            android.util.TypedValue ripple = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            tv.setBackgroundResource(ripple.resourceId);
            tv.setOnClickListener(v -> {
                // Deselect previous
                if (selectedIndex[0] >= 0) {
                    reasonRows[selectedIndex[0]].setTextColor(0xFF1C1C1E);
                }
                selectedIndex[0] = idx;
                tv.setTextColor(0xFF007AFF);
            });
            reasonRows[i] = tv;
            group1.addView(tv, matchW());
        }
        root.addView(group1, matchW());
        addGroupGap(root);

        // Group 2: description EditText (no border)
        LinearLayout group2 = buildIosGroup();
        EditText etCustomReason = new EditText(context);
        etCustomReason.setHint("Mô tả chi tiết (không bắt buộc)");
        etCustomReason.setMinLines(2);
        etCustomReason.setPadding(dpPx(20), dpPx(14), dpPx(20), dpPx(14));
        etCustomReason.setBackground(null);
        etCustomReason.setTextSize(15);
        etCustomReason.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etCustomReason.setFocusable(true);
        etCustomReason.setFocusableInTouchMode(true);
        etCustomReason.setClickable(true);
        group2.addView(etCustomReason, matchW());
        root.addView(group2, matchW());
        addGroupGap(root);

        // Group 3: submit button
        LinearLayout group3 = buildIosGroup();
        addIosRow(group3, "Gửi báo cáo", 0xFF007AFF, v -> {
            if (selectedIndex[0] == -1) {
                Toast.makeText(context, "Vui lòng chọn lý do báo cáo", Toast.LENGTH_SHORT).show();
                return;
            }
            sheet.dismiss();

            String selectedReason = reasons[selectedIndex[0]];
            String description = etCustomReason.getText().toString().trim();
            long postId = 0;
            try {
                postId = Long.parseLong(post.getId());
            } catch (Exception ignored) {}

            if (postId > 0) {
                com.example.weconnect.api.RetrofitClient.loadToken(context);
                com.example.weconnect.api.ReportApiService reportApi =
                        com.example.weconnect.api.RetrofitClient.getClient()
                                .create(com.example.weconnect.api.ReportApiService.class);

                java.util.Map<String, Object> body = new java.util.HashMap<>();
                body.put("targetType", "POST");
                body.put("targetId", postId);
                body.put("reason", selectedReason);
                body.put("description", description);

                reportApi.createReport(body).enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<Void>>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<Void>> call,
                                           retrofit2.Response<com.example.weconnect.models.ApiResponse<Void>> response) {
                        Toast.makeText(context, "✅ Đã gửi báo cáo vi phạm. Cảm ơn bạn!", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<Void>> call, Throwable t) {
                        Toast.makeText(context, "Lỗi kết nối. Thử lại sau!", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(context, "✅ Đã gửi báo cáo vi phạm. Cảm ơn bạn!", Toast.LENGTH_LONG).show();
            }
            hidePost(position);
        });
        root.addView(group3, matchW());
        addGroupGap(root);

        // Group 4: cancel
        LinearLayout group4 = buildIosGroup();
        addIosRow(group4, "Huỷ", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group4, matchW());

        android.widget.ScrollView sv = new android.widget.ScrollView(context);
        sv.addView(root);
        sheet.setContentView(sv);
        makeSheetTransparent(root);
        sheet.show();
    }

    private void bindTimeRange(PostViewHolder holder, Post post) {
        long start = post.getStartTimeMillis();
        long end = post.getEndTimeMillis();
        String activityEndStr = post.getActivityEndTimeStr();
        String activityTimeType = post.getActivityTimeType();

        if (start <= 0) {
            holder.layoutTimeRange.setVisibility(View.GONE);
            return;
        }

        holder.layoutTimeRange.setVisibility(View.VISIBLE);

        SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        Date startDate = new Date(start);

        if (activityEndStr != null && !activityEndStr.isEmpty()) {
            try {
                Date actEndDate = isoFmt.parse(activityEndStr);
                if ("CONTINUOUS_RANGE".equals(activityTimeType)) {
                    holder.tvDateRange.setText("🟢 Bắt đầu: " + dateFmt.format(startDate) + ", " + timeFmt.format(startDate));
                    holder.tvTimeSlot.setText("🔴 Kết thúc: " + dateFmt.format(actEndDate) + ", " + timeFmt.format(actEndDate));
                    holder.tvTimeSlot.setVisibility(View.VISIBLE);
                } else {
                    // DAILY_TIME_SLOT (default)
                    String startDateStr = dateFmt.format(startDate);
                    String endDateStr = dateFmt.format(actEndDate);
                    if (startDateStr.equals(endDateStr)) {
                        holder.tvDateRange.setText("📅 Ngày: " + startDateStr);
                    } else {
                        holder.tvDateRange.setText("📅 Ngày: " + startDateStr + " - " + endDateStr);
                    }
                    holder.tvTimeSlot.setText("⏰ Mỗi ngày: " + timeFmt.format(startDate) + " - " + timeFmt.format(actEndDate));
                    holder.tvTimeSlot.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                holder.tvDateRange.setText("📅 " + DATE_FORMAT.format(startDate));
                holder.tvTimeSlot.setVisibility(View.GONE);
            }
        } else if (end > 0) {
            holder.tvDateRange.setText("📅 " + DATE_FORMAT.format(startDate) + " - " + DATE_FORMAT.format(new Date(end)));
            holder.tvTimeSlot.setVisibility(View.GONE);
        } else {
            holder.layoutTimeRange.setVisibility(View.GONE);
            return;
        }

        // Countdown badge based on post expiry
        if (end > 0) {
            long remaining = end - System.currentTimeMillis();
            if (remaining > 0 && remaining <= 24L * 60L * 60L * 1000L) {
                holder.tvCountdown.setVisibility(View.VISIBLE);
                holder.tvCountdown.setText("⏰ " + formatCountdown(remaining));
            } else if (remaining <= 0) {
                holder.tvCountdown.setVisibility(View.VISIBLE);
                holder.tvCountdown.setText("Hết hạn");
            } else {
                holder.tvCountdown.setVisibility(View.GONE);
            }
        } else {
            holder.tvCountdown.setVisibility(View.GONE);
        }
    }

    private String formatCountdown(long millis) {
        long hours = millis / (60L * 60L * 1000L);
        long minutes = (millis % (60L * 60L * 1000L)) / (60L * 1000L);

        if (hours > 0 && minutes > 0) {
            return "Còn " + hours + " giờ " + minutes + " phút";
        } else if (hours > 0) {
            return "Còn " + hours + " giờ";
        } else {
            return "Còn " + minutes + " phút";
        }
    }

    private void bindActiveButtons(PostViewHolder holder, Post post, int position) {
        boolean isOwnPost = currentUsername.equalsIgnoreCase(post.getUsername());

        if (isOwnPost) {
            // Own post: show "Nhóm chat" to open the group chat
            holder.btnJoinGroup.setVisibility(View.VISIBLE);
            holder.btnJoinGroup.setText("💬 Nhóm chat");
            holder.btnJoinGroup.setEnabled(true);
            holder.btnJoinGroup.setAlpha(1.0f);
            holder.btnJoinGroup.setOnClickListener(v -> openGroupChatForPost(post));
        } else {
            // Other's post: check if tag matches viewer's interests
            boolean tagMatchesViewer = true;
            if (viewerInterests != null && !viewerInterests.isEmpty()) {
                String postTag = post.getInterestTag();
                tagMatchesViewer = postTag != null
                        && viewerInterests.contains(postTag.trim().toLowerCase());
            }

            if (!tagMatchesViewer) {
                // Tag không trùng → chỉ hiện nút thành viên, không hiện tham gia
                holder.btnJoinGroup.setVisibility(View.GONE);
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        (int) (48 * holder.itemView.getResources().getDisplayMetrics().density));
                params.weight = 0;
                holder.btnViewMembers.setLayoutParams(params);
                holder.btnViewMembers.setPadding(48, 0, 48, 0);
            } else {
                // Tag trùng → hiện nút tham gia
                holder.btnJoinGroup.setVisibility(View.VISIBLE);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    0,
                    (int) (48 * holder.itemView.getResources().getDisplayMetrics().density));
            params.weight = 1;
            params.setMarginStart((int) (6 * holder.itemView.getResources().getDisplayMetrics().density));
            holder.btnViewMembers.setLayoutParams(params);
            holder.btnViewMembers.setPadding(0, 0, 0, 0);

            if (post.isJoined()) {
                // Already joined: show "Mở đoạn chat" to open group conversation
                holder.btnJoinGroup.setText("💬 Mở đoạn chat");
                holder.btnJoinGroup.setEnabled(true);
                holder.btnJoinGroup.setAlpha(1.0f);
                holder.btnJoinGroup.setOnClickListener(v -> {
                    String postId = post.getId() != null ? post.getId() : "";
                    if (postId.isEmpty()) {
                        android.widget.Toast.makeText(context, "Lỗi: không có ID bài viết", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long postIdLong;
                    try {
                        postIdLong = Long.parseLong(postId);
                    } catch (NumberFormatException e) {
                        android.widget.Toast.makeText(context, "Lỗi ID bài viết", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    com.example.weconnect.api.RetrofitClient.loadToken(context);
                    com.example.weconnect.api.ChatApiService chatApi =
                            com.example.weconnect.api.RetrofitClient.getClient()
                                    .create(com.example.weconnect.api.ChatApiService.class);

                    chatApi.getRoomByPostId(postIdLong).enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>>() {
                        @Override
                        public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call,
                                               retrofit2.Response<com.example.weconnect.models.ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getResult() != null) {
                                com.example.weconnect.models.ChatRoomApiResponse room = response.body().getResult();
                                long roomId = room.getId();
                                if (roomId > 0) {
                                    Intent intent = new Intent(context, com.example.weconnect.activities.ConversationActivity.class);
                                    intent.putExtra("room_id", roomId);
                                    context.startActivity(intent);
                                } else {
                                    android.widget.Toast.makeText(context, "Lỗi phòng chat", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Không tìm thấy phòng chat cho bài viết này", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call, Throwable t) {
                            android.widget.Toast.makeText(context, "Lỗi kết nối", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            } else if (post.isPendingApproval()) {
                // Pending approval: show dimmed "Đang chờ duyệt"
                holder.btnJoinGroup.setText("⏳ Đang chờ duyệt");
                holder.btnJoinGroup.setEnabled(false);
                holder.btnJoinGroup.setAlpha(0.6f);
                holder.btnJoinGroup.setOnClickListener(null);
            } else if (post.getMaxMembers() > 0 && post.getMemberCount() >= post.getMaxMembers()) {
                holder.btnJoinGroup.setText("Đã đủ thành viên");
                holder.btnJoinGroup.setEnabled(false);
                holder.btnJoinGroup.setAlpha(0.6f);
                holder.btnJoinGroup.setOnClickListener(null);
            } else {
                // Not joined: show active "Tham gia" button
                holder.btnJoinGroup.setText("Tham gia");
                holder.btnJoinGroup.setEnabled(true);
                holder.btnJoinGroup.setAlpha(1.0f);
                holder.btnJoinGroup.setOnClickListener(v -> {
                    // Try real API first
                    com.example.weconnect.api.RetrofitClient.loadToken(context);
                    String token = com.example.weconnect.api.RetrofitClient.getAuthToken();
                    
                    if (token != null && post.getId() != null && !post.getId().isEmpty()) {
                        com.example.weconnect.api.PostApiService postApi = 
                                com.example.weconnect.api.RetrofitClient.getClient()
                                        .create(com.example.weconnect.api.PostApiService.class);
                        
                        postApi.joinPost(Long.parseLong(post.getId())).enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<Void>>() {
                            @Override
                            public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<Void>> call,
                                                   retrofit2.Response<com.example.weconnect.models.ApiResponse<Void>> response) {
                                if (response.isSuccessful()) {
                                    post.setPendingApproval(true);
                                    holder.btnJoinGroup.setText("⏳ Đang chờ duyệt");
                                    holder.btnJoinGroup.setEnabled(false);
                                    holder.btnJoinGroup.setAlpha(0.6f);
                                    Toast.makeText(context, "Đã gửi yêu cầu tham gia!", Toast.LENGTH_SHORT).show();
                                } else {
                                    String errorMsg = "Lỗi khi gửi yêu cầu. Thử lại!";
                                    try {
                                        if (response.errorBody() != null) {
                                            String body = response.errorBody().string();
                                            org.json.JSONObject json = new org.json.JSONObject(body);
                                            if (json.has("message")) errorMsg = json.getString("message");
                                        }
                                    } catch (Exception ignored) {}
                                    if (errorMsg.contains("đủ thành viên")) {
                                        post.setMemberCount(post.getMaxMembers());
                                        holder.btnJoinGroup.setText("Đã đủ thành viên");
                                        holder.btnJoinGroup.setEnabled(false);
                                        holder.btnJoinGroup.setAlpha(0.6f);
                                        holder.btnJoinGroup.setOnClickListener(null);
                                    }
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<Void>> call, Throwable t) {
                                Toast.makeText(context, "Lỗi kết nối. Thử lại!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        // Fallback: local only
                        post.setPendingApproval(true);
                        Toast.makeText(context, "Đã gửi yêu cầu tham gia " + post.getUsername(), Toast.LENGTH_SHORT).show();
                        holder.btnJoinGroup.setText("⏳ Đang chờ duyệt");
                        holder.btnJoinGroup.setEnabled(false);
                        holder.btnJoinGroup.setAlpha(0.6f);
                    }
                });
            }
            } // close tagMatchesViewer else
        }
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    private void openGroupChatForPost(Post post) {
        long postIdLong;
        try {
            postIdLong = Long.parseLong(post.getId());
        } catch (Exception e) {
            Toast.makeText(context, "Lỗi ID bài viết", Toast.LENGTH_SHORT).show();
            return;
        }
        com.example.weconnect.api.RetrofitClient.loadToken(context);
        com.example.weconnect.api.RetrofitClient.getClient()
                .create(com.example.weconnect.api.ChatApiService.class)
                .getRoomByPostId(postIdLong)
                .enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call,
                                           retrofit2.Response<com.example.weconnect.models.ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            long roomId = response.body().getResult().getId();
                            Intent intent = new Intent(context, com.example.weconnect.activities.ConversationActivity.class);
                            intent.putExtra("room_id", String.valueOf(roomId));
                            context.startActivity(intent);
                        } else {
                            Toast.makeText(context, "Không tìm thấy phòng chat", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call,
                                          Throwable t) {
                        Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openPostDetail(Post post) {
        Intent intent = new Intent(context, PostDetailActivity.class);
        intent.putExtra("post", post);
        context.startActivity(intent);
    }

    private void openUserProfile(String username, long authorId) {
        String currentUser = com.example.weconnect.api.RetrofitClient.getUserName(context);
        Intent intent = new Intent(context, UserProfileActivity.class);
        intent.putExtra("username", username);
        if (currentUser != null && !username.equalsIgnoreCase(currentUser)) {
            intent.putExtra("view_other", true);
            intent.putExtra("user_id", authorId);
        }
        context.startActivity(intent);
    }

    // ── iOS-style sheet helpers ──

    private LinearLayout buildIosRoot() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0x00000000);
        int p = dpPx(10);
        root.setPadding(p, 0, p, p);
        return root;
    }

    private LinearLayout buildIosGroup() {
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dpPx(14));
        ll.setBackground(bg);
        ll.setClipToOutline(true);
        return ll;
    }

    private void addIosHeader(LinearLayout parent, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF8E8E93);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(dpPx(16), dpPx(13), dpPx(16), dpPx(4));
        parent.addView(tv, matchW());
    }

    private void addIosRow(LinearLayout parent, String text, int color, View.OnClickListener listener) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setTextColor(color);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
        if (listener != null) {
            tv.setClickable(true);
            tv.setFocusable(true);
            android.util.TypedValue tv2 = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv2, true);
            tv.setBackgroundResource(tv2.resourceId);
            tv.setOnClickListener(listener);
        }
        parent.addView(tv, matchW());
    }

    private void addIosSep(LinearLayout parent) {
        View sep = new View(context);
        sep.setBackgroundColor(0xFFD1D1D6);
        sep.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        parent.addView(sep);
    }

    private void addGroupGap(LinearLayout parent) {
        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(8)));
        parent.addView(gap);
    }

    private void makeSheetTransparent(View root) {
        root.post(() -> {
            if (root.getParent() instanceof View) {
                ((View) root.getParent()).setBackgroundColor(0x00000000);
            }
        });
    }

    private LinearLayout.LayoutParams matchW() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dpPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivPostImage, ivPostMenu;
        View cvPostImage;
        TextView tvUsername, tvTime, tvContent;
        TextView btnJoinGroup, btnViewMembers;
        TextView tvTag, tvLocation;
        LinearLayout layoutTimeRange, layoutActiveButtons;
        TextView tvDateRange, tvTimeSlot, tvCountdown, tvExpiredLabel, tvExpirationHours;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.post_item_avatar);
            tvUsername = itemView.findViewById(R.id.post_item_username);
            tvTime = itemView.findViewById(R.id.post_item_time);
            tvContent = itemView.findViewById(R.id.post_item_content);
            cvPostImage = itemView.findViewById(R.id.cvPostImage);
            ivPostImage = itemView.findViewById(R.id.ivPostImage);
            btnJoinGroup = itemView.findViewById(R.id.btnJoinGroup);
            btnViewMembers = itemView.findViewById(R.id.btnViewMembers);
            tvTag = itemView.findViewById(R.id.post_item_tag);
            tvLocation = itemView.findViewById(R.id.post_item_location);
            layoutTimeRange = itemView.findViewById(R.id.layoutTimeRange);
            tvDateRange = itemView.findViewById(R.id.post_item_date_range);
            tvTimeSlot = itemView.findViewById(R.id.post_item_time_slot);
            tvCountdown = itemView.findViewById(R.id.post_item_countdown);
            layoutActiveButtons = itemView.findViewById(R.id.layoutActiveButtons);
            tvExpiredLabel = itemView.findViewById(R.id.tvExpiredLabel);
            tvExpirationHours = itemView.findViewById(R.id.tvExpirationHours);
            ivPostMenu = itemView.findViewById(R.id.ivPostMenu);
        }
    }
}
