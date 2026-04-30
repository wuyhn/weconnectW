package com.example.weconnect.post.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.chat.data.ChatApiService;
import com.example.weconnect.post.data.PostApiService;
import com.example.weconnect.core.data.RetrofitClient;
import com.example.weconnect.core.data.ApiResponse;
import com.example.weconnect.chat.data.ChatRoomApiResponse;
import com.example.weconnect.post.data.Post;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostDetailActivity extends AppCompatActivity {

    private ImageView ivBackPostDetail;
    private TextView tvPostDetailUsername;
    private TextView tvPostDetailContent;
    private TextView tvPostDetailTag;
    private TextView tvPostDetailLocation;
    private TextView tvPostDetailMembers;
    private TextView tvPostDetailTime;
    private TextView tvPostDetailStatus;
    private MaterialButton btnOpenGroupChat;

    // Pending approval views
    private LinearLayout layoutPendingApproval;
    private LinearLayout layoutApproveReject;
    private TextView tvPendingLabel;
    private TextView tvApprovalResult;
    private MaterialButton btnApproveJoin;
    private MaterialButton btnRejectJoin;

    private String username;
    private Post post;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        initViews();
        setupClickListeners();
        bindPostData();
        handlePendingApproval();
    }

    private void initViews() {
        ivBackPostDetail = findViewById(R.id.ivBackPostDetail);
        tvPostDetailUsername = findViewById(R.id.tvPostDetailUsername);
        tvPostDetailContent = findViewById(R.id.tvPostDetailContent);
        tvPostDetailTag = findViewById(R.id.tvPostDetailTag);
        tvPostDetailLocation = findViewById(R.id.tvPostDetailLocation);
        tvPostDetailMembers = findViewById(R.id.tvPostDetailMembers);
        tvPostDetailTime = findViewById(R.id.tvPostDetailTime);
        tvPostDetailStatus = findViewById(R.id.tvPostDetailStatus);
        btnOpenGroupChat = findViewById(R.id.btnOpenGroupChat);

        layoutPendingApproval = findViewById(R.id.layoutPendingApproval);
        layoutApproveReject = findViewById(R.id.layoutApproveReject);
        tvPendingLabel = findViewById(R.id.tvPendingLabel);
        tvApprovalResult = findViewById(R.id.tvApprovalResult);
        btnApproveJoin = findViewById(R.id.btnApproveJoin);
        btnRejectJoin = findViewById(R.id.btnRejectJoin);
    }

    private void setupClickListeners() {
        ivBackPostDetail.setOnClickListener(v -> finish());

        tvPostDetailUsername.setOnClickListener(v -> {
            Intent intent = new Intent(PostDetailActivity.this, UserProfileActivity.class);
            intent.putExtra("username", username);
            if (post != null) {
                String currentUser = RetrofitClient.getUserName(this);
                if (currentUser == null || !username.equalsIgnoreCase(currentUser)) {
                    intent.putExtra("view_other", true);
                    if (post.getAuthorId() > 0) {
                        intent.putExtra("user_id", post.getAuthorId());
                    }
                }
            }
            startActivity(intent);
        });

        // Bấm vào thành viên → mở danh sách người tham gia
        tvPostDetailMembers.setOnClickListener(v -> {
            if (post != null) {
                Intent intent = new Intent(PostDetailActivity.this, ParticipantsActivity.class);
                intent.putExtra("post_id", post.getId());
                intent.putExtra("post_author", post.getUsername());
                intent.putExtra("member_count", post.getMemberCount());
                intent.putExtra("max_members", post.getMaxMembers());
                startActivity(intent);
            }
        });

        btnOpenGroupChat.setOnClickListener(v -> openRelatedGroupChat());
    }

    private void bindPostData() {
        post = (Post) getIntent().getSerializableExtra("post");
        if (post == null) {
            finish();
            return;
        }

        username = post.getUsername();
        tvPostDetailUsername.setText(username);
        tvPostDetailContent.setText(post.getContent());
        tvPostDetailMembers.setText("Thành viên: " + post.getMemberCount() + "/" + post.getMaxMembers());
        tvPostDetailTime.setText("Đăng lúc: " + post.getTimeAgo());
        tvPostDetailStatus.setText("Trạng thái: " + post.getStatusLabel());

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

        // Show group chat and joined state
        if (post.isJoined()) {
            btnOpenGroupChat.setVisibility(View.VISIBLE);
            btnOpenGroupChat.setText("💬 Mở nhóm chat");
        } else {
            btnOpenGroupChat.setVisibility(View.GONE);
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

                    // Backend PostService.approveMember() đã tự thêm member vào activity room rồi
                    // Không cần gọi FakeChatRepository nữa

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(PostDetailActivity.this)
                            .setTitle("Đã duyệt!")
                            .setMessage("Bạn đã duyệt " + (userName != null ? userName : "người dùng") + " tham gia hoạt động.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    Toast.makeText(PostDetailActivity.this, "Lỗi khi duyệt", Toast.LENGTH_SHORT).show();
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

    /**
     * Mở nhóm chat liên kết với bài post, dùng API thay vì FakeChatRepository.
     */
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
