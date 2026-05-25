package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.ChatApiService;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ChatRoomApiResponse;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.PostResponse;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostDetailActivity extends AppCompatActivity {

    private ImageView ivBackPostDetail;
    private ImageView ivPostDetailAuthorAvatar;
    private ImageView ivPostDetailImage;
    private android.view.View cvPostDetailImage;
    private android.widget.LinearLayout layoutPostDetailAuthor;
    private TextView tvPostDetailUsername;
    private TextView tvPostDetailContent;
    private TextView tvPostDetailTag;
    private TextView tvPostDetailLocation;
    private TextView tvPostDetailActivityDate;
    private TextView tvPostDetailActivityTime;
    private TextView tvPostDetailTime;
    private TextView tvPostDetailStatus;
    private android.widget.Button btnDetailJoin;
    private android.widget.Button btnDetailMembers;

    // Pending approval views
    private LinearLayout layoutPendingApproval;
    private LinearLayout layoutApproveReject;
    private TextView tvPendingLabel;
    private TextView tvApprovalResult;
    private MaterialButton btnApproveJoin;
    private MaterialButton btnRejectJoin;

    private String username;
    private Post post;
    private boolean isFirstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        initViews();
        setupClickListeners();
        bindPostData();
        handlePendingApproval();
        refreshMemberCount();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFirstResume) {
            isFirstResume = false;
            return;
        }
        // Re-fetch member count khi quay lại từ PendingListActivity / ParticipantsActivity
        refreshMemberCount();
    }

    private void refreshMemberCount() {
        if (post == null || post.getId() == null) return;
        long postId;
        try {
            postId = Long.parseLong(post.getId());
        } catch (NumberFormatException e) { return; }

        RetrofitClient.loadToken(this);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);
        postApi.getPost(postId).enqueue(new Callback<ApiResponse<PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PostResponse>> call,
                                   Response<ApiResponse<PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    PostResponse pr = response.body().getResult();
                    post.setMemberCount(pr.getMemberCount());
                    post.setMaxMembers(pr.getMaxMembers());
                    post.setCancelled(pr.isCancelled());
                    if (pr.isCancelled()) {
                        showCancelledState();
                    } else {
                        updateMemberCountUI();
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PostResponse>> call, Throwable t) { }
        });
    }

    private void showCancelledState() {
        tvPostDetailStatus.setText("Hoạt động không khả dụng.");
        tvPostDetailStatus.setTextColor(0xFFFF3B30);
        btnDetailJoin.setText("Hoạt động không khả dụng");
        btnDetailJoin.setEnabled(false);
        btnDetailJoin.setAlpha(0.5f);
        btnDetailJoin.setOnClickListener(null);
        if (layoutPendingApproval != null) layoutPendingApproval.setVisibility(View.GONE);
    }

    private void updateMemberCountUI() {
        btnDetailMembers.setText("👥 " + post.getMemberCount() + "/" + post.getMaxMembers());

        // Chỉ cập nhật join button nếu user chưa joined / pending
        String currentUser = RetrofitClient.getUserName(this);
        long myId = RetrofitClient.getUserId(this);
        boolean isOwnPost = (currentUser != null && currentUser.equalsIgnoreCase(username))
                || (myId > 0 && post.getAuthorId() == myId);

        if (!isOwnPost && !post.isJoined() && !post.isPendingApproval()) {
            if (post.isArchived() || post.isExpired()) {
                btnDetailJoin.setText("Hoạt động đã kết thúc");
                btnDetailJoin.setEnabled(false);
                btnDetailJoin.setAlpha(0.5f);
                btnDetailJoin.setOnClickListener(null);
            } else if (post.getMaxMembers() > 0 && post.getMemberCount() >= post.getMaxMembers()) {
                btnDetailJoin.setText("Đã đủ thành viên");
                btnDetailJoin.setEnabled(false);
                btnDetailJoin.setAlpha(0.6f);
                btnDetailJoin.setOnClickListener(null);
            } else {
                btnDetailJoin.setText("Tham gia");
                btnDetailJoin.setEnabled(true);
                btnDetailJoin.setAlpha(1f);
                btnDetailJoin.setOnClickListener(v -> joinPost());
            }
        }
    }

    private void initViews() {
        ivBackPostDetail = findViewById(R.id.ivBackPostDetail);
        ivPostDetailAuthorAvatar = findViewById(R.id.ivPostDetailAuthorAvatar);
        ivPostDetailImage = findViewById(R.id.ivPostDetailImage);
        cvPostDetailImage = findViewById(R.id.cvPostDetailImage);
        layoutPostDetailAuthor = findViewById(R.id.layoutPostDetailAuthor);
        tvPostDetailUsername = findViewById(R.id.tvPostDetailUsername);
        tvPostDetailContent = findViewById(R.id.tvPostDetailContent);
        tvPostDetailTag = findViewById(R.id.tvPostDetailTag);
        tvPostDetailLocation = findViewById(R.id.tvPostDetailLocation);
        tvPostDetailActivityDate = findViewById(R.id.tvPostDetailActivityDate);
        tvPostDetailActivityTime = findViewById(R.id.tvPostDetailActivityTime);
        tvPostDetailTime = findViewById(R.id.tvPostDetailTime);
        tvPostDetailStatus = findViewById(R.id.tvPostDetailStatus);
        btnDetailJoin = findViewById(R.id.btnDetailJoin);
        btnDetailMembers = findViewById(R.id.btnDetailMembers);

        layoutPendingApproval = findViewById(R.id.layoutPendingApproval);
        layoutApproveReject = findViewById(R.id.layoutApproveReject);
        tvPendingLabel = findViewById(R.id.tvPendingLabel);
        tvApprovalResult = findViewById(R.id.tvApprovalResult);
        btnApproveJoin = findViewById(R.id.btnApproveJoin);
        btnRejectJoin = findViewById(R.id.btnRejectJoin);
    }

    private void setupClickListeners() {
        ivBackPostDetail.setOnClickListener(v -> finish());

        android.view.View.OnClickListener authorClickListener = v -> {
            if (post == null) return;
            Intent intent = new Intent(PostDetailActivity.this, UserProfileActivity.class);
            intent.putExtra("username", username);
            String currentUser = RetrofitClient.getUserName(this);
            if (currentUser == null || !username.equalsIgnoreCase(currentUser)) {
                intent.putExtra("view_other", true);
                if (post.getAuthorId() > 0) intent.putExtra("user_id", post.getAuthorId());
            }
            startActivity(intent);
        };
        tvPostDetailUsername.setOnClickListener(authorClickListener);
        if (layoutPostDetailAuthor != null) layoutPostDetailAuthor.setOnClickListener(authorClickListener);
        if (ivPostDetailAuthorAvatar != null) ivPostDetailAuthorAvatar.setOnClickListener(authorClickListener);

    }

    private void bindPostData() {
        post = (Post) getIntent().getSerializableExtra("post");
        if (post == null) {
            finish();
            return;
        }

        username = post.getUsername();
        tvPostDetailUsername.setText(username);

        // Load author avatar: global cache → post avatarUrl → placeholder
        if (ivPostDetailAuthorAvatar != null) {
            String avatarUrl = post.getAuthorId() > 0
                    ? RetrofitClient.getCachedAvatarForUser(post.getAuthorId()) : null;
            if (avatarUrl == null || avatarUrl.isEmpty()) avatarUrl = post.getAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (avatarUrl.startsWith("/")) avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                com.bumptech.glide.Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .circleCrop()
                        .into(ivPostDetailAuthorAvatar);
            }
        }
        tvPostDetailContent.setText(post.getContent());

        // Show activity date/time rows if activityEndTime is set
        String activityEndTimeStr = post.getActivityEndTimeStr();
        String activityTimeType = post.getActivityTimeType();
        if (activityEndTimeStr != null && !activityEndTimeStr.isEmpty()) {
            java.text.SimpleDateFormat isoFmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            try {
                java.util.Date actEndDate = isoFmt.parse(activityEndTimeStr);
                java.util.Date actStartDate = new java.util.Date(post.getStartTimeMillis());

                java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
                java.text.SimpleDateFormat timeFmt = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());

                if ("CONTINUOUS_RANGE".equals(activityTimeType)) {
                    if (tvPostDetailActivityDate != null) {
                        tvPostDetailActivityDate.setText("🟢 Bắt đầu: " + dateFmt.format(actStartDate) + ", " + timeFmt.format(actStartDate));
                        tvPostDetailActivityDate.setVisibility(android.view.View.VISIBLE);
                    }
                    if (tvPostDetailActivityTime != null) {
                        tvPostDetailActivityTime.setText("🔴 Kết thúc: " + dateFmt.format(actEndDate) + ", " + timeFmt.format(actEndDate));
                        tvPostDetailActivityTime.setVisibility(android.view.View.VISIBLE);
                    }
                } else {
                    // DAILY_TIME_SLOT (default)
                    String startDateStr = dateFmt.format(actStartDate);
                    String endDateStr = dateFmt.format(actEndDate);
                    String dateLabel = startDateStr.equals(endDateStr)
                            ? "📅 Ngày: " + startDateStr
                            : "📅 Ngày: " + startDateStr + " - " + endDateStr;
                    if (tvPostDetailActivityDate != null) {
                        tvPostDetailActivityDate.setText(dateLabel);
                        tvPostDetailActivityDate.setVisibility(android.view.View.VISIBLE);
                    }
                    if (tvPostDetailActivityTime != null) {
                        tvPostDetailActivityTime.setText("⏰ Mỗi ngày: " + timeFmt.format(actStartDate) + " - " + timeFmt.format(actEndDate));
                        tvPostDetailActivityTime.setVisibility(android.view.View.VISIBLE);
                    }
                }

                // Post expiry = activity end time → show remaining time
                long now = System.currentTimeMillis();
                long diffMs = actEndDate.getTime() - now;
                String expiryLabel;
                if (diffMs <= 0) {
                    expiryLabel = "Đã hết hạn";
                } else {
                    long diffMin = diffMs / (60_000L);
                    long diffHour = diffMin / 60;
                    long diffDay = diffHour / 24;
                    if (diffDay >= 1) {
                        long remHours = diffHour % 24;
                        expiryLabel = "Còn " + diffDay + " ngày" + (remHours > 0 ? " " + remHours + " giờ" : "");
                    } else if (diffHour >= 1) {
                        expiryLabel = "Còn " + diffHour + " giờ";
                    } else {
                        expiryLabel = "Còn " + diffMin + " phút";
                    }
                }
                String postedDateStr = post.getPostedDate();
                String postedLine = (postedDateStr != null && !postedDateStr.isEmpty())
                        ? "🕐 Đăng lúc: " + postedDateStr + "\n" : "";
                tvPostDetailTime.setText(postedLine + "⏳ Thời hạn bài viết: " + expiryLabel);
            } catch (Exception e) {
                if (tvPostDetailActivityDate != null) tvPostDetailActivityDate.setVisibility(android.view.View.GONE);
                if (tvPostDetailActivityTime != null) tvPostDetailActivityTime.setVisibility(android.view.View.GONE);
                tvPostDetailTime.setText(post.getPostedDate() != null ? "📅 " + post.getPostedDate() : "");
            }
        } else {
            // Old posts without activityEndTime — fall back to posted date display
            if (tvPostDetailActivityDate != null) tvPostDetailActivityDate.setVisibility(android.view.View.GONE);
            if (tvPostDetailActivityTime != null) tvPostDetailActivityTime.setVisibility(android.view.View.GONE);
            String postedDate = post.getPostedDate();
            String timeAgo = post.getTimeAgo();
            if (postedDate != null && !postedDate.isEmpty()) {
                tvPostDetailTime.setText("📅 " + postedDate
                        + (timeAgo != null && !timeAgo.isEmpty() ? "  ·  " + timeAgo : ""));
            } else {
                tvPostDetailTime.setText(timeAgo != null ? timeAgo : "");
            }
        }

        tvPostDetailStatus.setText("Trạng thái: " + post.getStatusLabel());

        // Load post image
        String postImageUrl = post.getPostImageUri();
        if (postImageUrl != null && !postImageUrl.isEmpty()) {
            if (cvPostDetailImage != null) cvPostDetailImage.setVisibility(View.VISIBLE);
            if (ivPostDetailImage != null) {
                if (postImageUrl.startsWith("/")) {
                    postImageUrl = RetrofitClient.getBaseUrl() + postImageUrl.substring(1);
                }
                com.bumptech.glide.Glide.with(this)
                        .load(postImageUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .into(ivPostDetailImage);
            }
        } else {
            if (cvPostDetailImage != null) cvPostDetailImage.setVisibility(View.GONE);
        }

        if (post.getInterestTag() != null && post.getInterestTag().length() > 0) {
            tvPostDetailTag.setVisibility(View.VISIBLE);
            tvPostDetailTag.setText(post.getInterestTag());
        } else {
            tvPostDetailTag.setVisibility(View.GONE);
        }

        if (post.getLocation() != null && post.getLocation().length() > 0) {
            tvPostDetailLocation.setVisibility(View.VISIBLE);
            tvPostDetailLocation.setText("Địa điểm: " + post.getLocation());
        } else {
            tvPostDetailLocation.setVisibility(View.GONE);
        }

        // Members button — always visible
        btnDetailMembers.setText("👥 " + post.getMemberCount() + "/" + post.getMaxMembers());
        btnDetailMembers.setOnClickListener(v -> {
            Intent intent = new Intent(PostDetailActivity.this, ParticipantsActivity.class);
            intent.putExtra("post_id", post.getId());
            intent.putExtra("post_author", post.getUsername());
            intent.putExtra("member_count", post.getMemberCount());
            intent.putExtra("max_members", post.getMaxMembers());
            intent.putExtra("author_user_id", post.getAuthorId());
            startActivity(intent);
        });

        // Hoạt động đã bị hủy — disabled toàn bộ
        if (post.isCancelled()) {
            tvPostDetailStatus.setText("Hoạt động không khả dụng.");
            tvPostDetailStatus.setTextColor(0xFFFF3B30);
            btnDetailJoin.setText("Hoạt động không khả dụng");
            btnDetailJoin.setEnabled(false);
            btnDetailJoin.setAlpha(0.5f);
            btnDetailJoin.setOnClickListener(null);
            return;
        }

        // Join/Chat button — state theo role
        String currentUser = RetrofitClient.getUserName(this);
        long myId = RetrofitClient.getUserId(this);
        boolean isOwnPost = (currentUser != null && currentUser.equalsIgnoreCase(username))
                || (myId > 0 && post.getAuthorId() == myId);

        if (isOwnPost) {
            btnDetailJoin.setText("💬 Nhóm chat");
            btnDetailJoin.setEnabled(true);
            btnDetailJoin.setAlpha(1f);
            btnDetailJoin.setOnClickListener(v -> openRelatedGroupChat());
        } else if (post.isJoined()) {
            btnDetailJoin.setText("💬 Mở nhóm chat");
            btnDetailJoin.setEnabled(true);
            btnDetailJoin.setAlpha(1f);
            btnDetailJoin.setOnClickListener(v -> openRelatedGroupChat());
        } else if (post.isPendingApproval()) {
            btnDetailJoin.setText("⏳ Đang chờ duyệt");
            btnDetailJoin.setEnabled(false);
            btnDetailJoin.setAlpha(0.6f);
        } else if (post.isArchived() || post.isExpired()) {
            btnDetailJoin.setText("Hoạt động đã kết thúc");
            btnDetailJoin.setEnabled(false);
            btnDetailJoin.setAlpha(0.5f);
            btnDetailJoin.setOnClickListener(null);
        } else if (post.getMaxMembers() > 0 && post.getMemberCount() >= post.getMaxMembers()) {
            btnDetailJoin.setText("Đã đủ thành viên");
            btnDetailJoin.setEnabled(false);
            btnDetailJoin.setAlpha(0.6f);
            btnDetailJoin.setOnClickListener(null);
        } else {
            btnDetailJoin.setText("Tham gia");
            btnDetailJoin.setEnabled(true);
            btnDetailJoin.setAlpha(1f);
            btnDetailJoin.setOnClickListener(v -> joinPost());
        }
    }

    private void handlePendingApproval() {
        boolean showPending = getIntent().getBooleanExtra("show_pending_actions", false);
        long pendingUserId = getIntent().getLongExtra("pending_user_id", -1);
        String pendingUsername = getIntent().getStringExtra("pending_username");

        if (showPending && post != null && pendingUserId > 0) {
            layoutPendingApproval.setVisibility(View.VISIBLE);
            tvPendingLabel.setText("👤 " + (pendingUsername != null ? pendingUsername : "Người dùng")
                    + " muốn tham gia hoạt động này");

            btnApproveJoin.setOnClickListener(v -> {
                approveUser(pendingUserId, pendingUsername);
            });

            btnRejectJoin.setOnClickListener(v -> {
                rejectUser(pendingUserId, pendingUsername);
            });
        } else {
            if (layoutPendingApproval != null) {
                layoutPendingApproval.setVisibility(View.GONE);
            }
        }
    }

    private void approveUser(long userId, String userName) {
        if (post == null) return;
        long postId;
        try {
            postId = Long.parseLong(post.getId());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Lỗi ID bài viết", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.loadToken(this);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);

        postApi.approveMember(postId, userId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    layoutApproveReject.setVisibility(View.GONE);
                    tvApprovalResult.setVisibility(View.VISIBLE);
                    tvApprovalResult.setText("✅ Đã chấp nhận " + (userName != null ? userName : "người dùng"));

                    // Cập nhật count ngay lập tức không cần chờ onResume
                    post.setMemberCount(post.getMemberCount() + 1);
                    updateMemberCountUI();

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(PostDetailActivity.this)
                            .setTitle("Đã duyệt!")
                            .setMessage("Bạn đã duyệt " + (userName != null ? userName : "người dùng") + " tham gia hoạt động.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    String errorMsg = "Lỗi khi duyệt";
                    try {
                        if (response.errorBody() != null) {
                            String body = response.errorBody().string();
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            if (json.has("message")) errorMsg = json.getString("message");
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(PostDetailActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(PostDetailActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void rejectUser(long userId, String userName) {
        if (post == null) return;
        long postId;
        try {
            postId = Long.parseLong(post.getId());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Lỗi ID bài viết", Toast.LENGTH_SHORT).show();
            return;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Xác nhận từ chối")
                .setMessage("Bạn có chắc chắn muốn từ chối " + (userName != null ? userName : "người dùng") + "?")
                .setPositiveButton("Từ chối", (d, w) -> {
                    RetrofitClient.loadToken(this);
                    PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);

                    postApi.rejectMember(postId, userId).enqueue(new Callback<ApiResponse<Void>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                            if (response.isSuccessful()) {
                                layoutApproveReject.setVisibility(View.GONE);
                                tvApprovalResult.setVisibility(View.VISIBLE);
                                tvApprovalResult.setText("❌ Đã từ chối " + (userName != null ? userName : "người dùng"));
                            } else {
                                Toast.makeText(PostDetailActivity.this, "Lỗi khi từ chối", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                            Toast.makeText(PostDetailActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void joinPost() {
        if (post == null) return;
        long postId;
        try {
            postId = Long.parseLong(post.getId());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Lỗi ID bài viết", Toast.LENGTH_SHORT).show();
            return;
        }
        RetrofitClient.loadToken(this);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);
        postApi.joinPost(postId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> resp) {
                if (resp.isSuccessful()) {
                    post.setPendingApproval(true);
                    btnDetailJoin.setText("⏳ Đang chờ duyệt");
                    btnDetailJoin.setEnabled(false);
                    btnDetailJoin.setAlpha(0.6f);
                    Toast.makeText(PostDetailActivity.this, "Đã gửi yêu cầu tham gia!", Toast.LENGTH_SHORT).show();
                } else {
                    String errorMsg = "Không thể tham gia. Thử lại.";
                    try {
                        if (resp.errorBody() != null) {
                            String body = resp.errorBody().string();
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            if (json.has("message")) errorMsg = json.getString("message");
                        }
                    } catch (Exception ignored) {}
                    if (errorMsg.contains("đủ thành viên")) {
                        btnDetailJoin.setText("Đã đủ thành viên");
                        btnDetailJoin.setEnabled(false);
                        btnDetailJoin.setAlpha(0.6f);
                        btnDetailJoin.setOnClickListener(null);
                        post.setMemberCount(post.getMaxMembers());
                        updateMemberCountUI();
                    }
                    Toast.makeText(PostDetailActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(PostDetailActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openRelatedGroupChat() {
        if (post == null) return;

        long postId;
        try {
            postId = Long.parseLong(post.getId());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Lỗi ID bài viết", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.loadToken(this);
        ChatApiService chatApi = RetrofitClient.getClient().create(ChatApiService.class);

        chatApi.getRoomByPostId(postId).enqueue(new Callback<ApiResponse<ChatRoomApiResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ChatRoomApiResponse>> call,
                                   Response<ApiResponse<ChatRoomApiResponse>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    ChatRoomApiResponse room = response.body().getResult();
                    Intent intent = new Intent(PostDetailActivity.this, ConversationActivity.class);
                    intent.putExtra("room_id", String.valueOf(room.getId()));
                    startActivity(intent);
                } else {
                    Toast.makeText(PostDetailActivity.this,
                            "Không tìm thấy phòng chat cho hoạt động này", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ChatRoomApiResponse>> call, Throwable t) {
                Toast.makeText(PostDetailActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
