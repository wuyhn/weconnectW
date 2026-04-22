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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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

        // Load avatar from server URL with Glide, fallback to placeholder
        if (post.getAvatarUrl() != null && !post.getAvatarUrl().isEmpty()) {
            String avatarUrl = post.getAvatarUrl();
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
            if (imageUriStr.startsWith("/uploads/")) {
                // Server-hosted image — load via HTTP
                holder.cvPostImage.setVisibility(View.VISIBLE);
                String fullUrl = com.example.weconnect.api.RetrofitClient.getBaseUrl() + imageUriStr.substring(1);
                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL(fullUrl);
                        InputStream inputStream = url.openStream();
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        inputStream.close();
                        if (bitmap != null) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.ivPostImage.setImageBitmap(bitmap);
                            });
                        }
                    } catch (Exception e) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            holder.cvPostImage.setVisibility(View.GONE);
                        });
                    }
                }).start();
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
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(context.getResources().getColor(R.color.soft_beige, null));
        layout.setPadding(0, 32, 0, 32);

        // Header
        android.widget.TextView header = new android.widget.TextView(context);
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

        TextView tvEdit = createMenuItem("✏️  Chỉnh sửa bài viết");
        tvEdit.setOnClickListener(v -> {
            sheet.dismiss();
            showEditPostDialog(post, position);
        });
        layout.addView(tvEdit);

        TextView tvDelete = createMenuItem("🗑️  Xoá bài viết");
        tvDelete.setTextColor(0xFFE53935);
        tvDelete.setOnClickListener(v -> {
            sheet.dismiss();
            showDeleteConfirmation(post, position);
        });
        layout.addView(tvDelete);

        sheet.setContentView(layout);
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
        if (post.getPostImageUri() != null) {
            intent.putExtra("edit_image_uri", post.getPostImageUri());
        }
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).startActivityForResult(intent, 2001);
        }
    }

    private void showDeleteConfirmation(Post post, int position) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Xoá bài viết")
                .setMessage("Bạn có chắc chắn muốn xoá bài viết này không?")
                .setPositiveButton("Xác nhận xoá", (dialog, which) -> {
                    FakePostRepository.getInstance().removePost(post.getId());
                    postList.remove(position);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, postList.size());
                    Toast.makeText(context, "Đã xoá bài viết", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void showOtherPostMenu(Post post, int position) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 32, 0, 32);

        TextView tvHide = createMenuItem("👁‍🗨  Ẩn bài viết");
        tvHide.setOnClickListener(v -> {
            sheet.dismiss();
            hidePost(position);
        });
        layout.addView(tvHide);

        TextView tvReport = createMenuItem("🚩  Báo cáo bài viết");
        tvReport.setOnClickListener(v -> {
            sheet.dismiss();
            showReportDialog(post, position);
        });
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

        // Header
        android.widget.TextView header = new android.widget.TextView(context);
        header.setText("🚩 Báo cáo bài viết");
        header.setTextSize(20);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(context.getResources().getColor(R.color.primary_pink, null));
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 40, 0, 16);
        root.addView(header);

        android.widget.TextView subHeader = new android.widget.TextView(context);
        subHeader.setText("Chọn lý do báo cáo bài viết này");
        subHeader.setTextSize(14);
        subHeader.setTextColor(context.getResources().getColor(R.color.text_secondary, null));
        subHeader.setGravity(android.view.Gravity.CENTER);
        subHeader.setPadding(0, 0, 0, 24);
        root.addView(subHeader);

        View div = new View(context);
        div.setBackgroundColor(0xFFE8E4DE);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div);

        // Reasons card
        com.google.android.material.card.MaterialCardView reasonCard =
                new com.google.android.material.card.MaterialCardView(context);
        LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardP.setMargins(40, 24, 40, 0);
        reasonCard.setLayoutParams(cardP);
        reasonCard.setCardBackgroundColor(context.getResources().getColor(R.color.card_surface, null));
        reasonCard.setRadius(48f);
        reasonCard.setCardElevation(4f);
        reasonCard.setStrokeWidth(3);
        reasonCard.setStrokeColor(context.getResources().getColor(R.color.primary_pink, null));

        RadioGroup radioGroup = new RadioGroup(context);
        radioGroup.setPadding(40, 24, 40, 24);
        String[] reasons = {
                "Nội dung thô tục",
                "Vi phạm quy định cộng đồng",
                "Spam / Quảng cáo",
                "Thông tin sai lệch",
                "Quấy rối / Bắt nạt",
                "Khác"
        };
        for (int i = 0; i < reasons.length; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setText(reasons[i]);
            rb.setId(i);
            rb.setPadding(24, 20, 24, 20);
            rb.setTextSize(15);
            rb.setTextColor(context.getResources().getColor(R.color.text_primary, null));
            radioGroup.addView(rb);
        }
        reasonCard.addView(radioGroup);
        root.addView(reasonCard);

        // Custom text card
        com.google.android.material.card.MaterialCardView inputCard =
                new com.google.android.material.card.MaterialCardView(context);
        LinearLayout.LayoutParams inputP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputP.setMargins(40, 16, 40, 0);
        inputCard.setLayoutParams(inputP);
        inputCard.setCardBackgroundColor(context.getResources().getColor(R.color.card_surface, null));
        inputCard.setRadius(48f);
        inputCard.setCardElevation(4f);
        inputCard.setStrokeWidth(3);
        inputCard.setStrokeColor(context.getResources().getColor(R.color.primary_pink, null));

        EditText etCustomReason = new EditText(context);
        etCustomReason.setHint("Mô tả chi tiết (không bắt buộc)");
        etCustomReason.setMinLines(2);
        etCustomReason.setPadding(40, 28, 40, 28);
        etCustomReason.setBackground(null);
        etCustomReason.setTextSize(14);
        etCustomReason.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etCustomReason.setFocusable(true);
        etCustomReason.setFocusableInTouchMode(true);
        etCustomReason.setClickable(true);
        inputCard.addView(etCustomReason);
        root.addView(inputCard);

        // Send button
        com.google.android.material.button.MaterialButton btnSend =
                new com.google.android.material.button.MaterialButton(context);
        btnSend.setText("Gửi báo cáo");
        btnSend.setAllCaps(false);
        btnSend.setCornerRadius(72);
        btnSend.setTextSize(16);
        btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                context.getResources().getColor(R.color.primary_pink, null)));
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnP.setMargins(48, 32, 48, 0);
        btnSend.setLayoutParams(btnP);
        btnSend.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(context, "Vui lòng chọn lý do báo cáo", Toast.LENGTH_SHORT).show();
                return;
            }
            sheet.dismiss();

            // Gửi report lên backend
            String selectedReason = reasons[checkedId];
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
        root.addView(btnSend);

        android.widget.ScrollView sv = new android.widget.ScrollView(context);
        sv.addView(root);
        sheet.setContentView(sv);
        sheet.show();
    }

    private void bindTimeRange(PostViewHolder holder, Post post) {
        long start = post.getStartTimeMillis();
        long end = post.getEndTimeMillis();

        if (start > 0 && end > 0) {
            holder.layoutTimeRange.setVisibility(View.VISIBLE);

            String startDate = DATE_FORMAT.format(new Date(start));
            String endDate = DATE_FORMAT.format(new Date(end));
            holder.tvDateRange.setText("📅 " + startDate + " - " + endDate);

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
            holder.layoutTimeRange.setVisibility(View.GONE);
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
            // Own post: don't show join button, only show members centered
            holder.btnJoinGroup.setVisibility(View.GONE);

            // Remove weight so gravity="center" on parent works
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    (int) (48 * holder.itemView.getResources().getDisplayMetrics().density));
            params.weight = 0;
            holder.btnViewMembers.setLayoutParams(params);
            holder.btnViewMembers.setPadding(48, 0, 48, 0);
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
                                    Toast.makeText(context, "Lỗi khi gửi yêu cầu. Thử lại!", Toast.LENGTH_SHORT).show();
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
            tvCountdown = itemView.findViewById(R.id.post_item_countdown);
            layoutActiveButtons = itemView.findViewById(R.id.layoutActiveButtons);
            tvExpiredLabel = itemView.findViewById(R.id.tvExpiredLabel);
            tvExpirationHours = itemView.findViewById(R.id.tvExpirationHours);
            ivPostMenu = itemView.findViewById(R.id.ivPostMenu);
        }
    }
}
