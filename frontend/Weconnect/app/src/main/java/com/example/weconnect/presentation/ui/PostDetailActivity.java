package com.example.weconnect.presentation.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.databinding.ActivityPostDetailBinding;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestoreChatRepository;
import com.example.weconnect.data.repository.FirestorePostRepository;
import com.example.weconnect.domain.model.Post;

import java.util.Map;

public class PostDetailActivity extends AppCompatActivity {

    private ActivityPostDetailBinding binding;

    private String authorUid;
    private Post post;
    private FirestorePostRepository postRepo;
    private FirestoreChatRepository chatRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPostDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        postRepo = new FirestorePostRepository();
        chatRepo = new FirestoreChatRepository();

        setupClickListeners();
        bindPostData();
        handlePendingApproval();
    }

    private void setupClickListeners() {
        binding.ivBackPostDetail.setOnClickListener(v -> finish());

        binding.tvPostDetailUsername.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("user_uid", authorUid);
            startActivity(intent);
        });

        binding.tvPostDetailMembers.setOnClickListener(v -> {
            if (post != null) {
                Intent intent = new Intent(this, ParticipantsActivity.class);
                intent.putExtra("post_id", post.getId());
                intent.putExtra("post_author", post.getUsername());
                intent.putExtra("member_count", post.getMemberCount());
                intent.putExtra("max_members", post.getMaxMembers());
                startActivity(intent);
            }
        });

        binding.btnOpenGroupChat.setOnClickListener(v -> openRelatedGroupChat());
    }

    private void bindPostData() {
        post = (Post) getIntent().getSerializableExtra("post");
        if (post == null) { finish(); return; }

        authorUid = getIntent().getStringExtra("author_uid");
        if (authorUid == null) authorUid = String.valueOf(post.getAuthorId());

        binding.tvPostDetailUsername.setText(post.getUsername());
        binding.tvPostDetailContent.setText(post.getContent());
        binding.tvPostDetailMembers.setText("Thành viên: " + post.getMemberCount() + "/" + post.getMaxMembers());
        binding.tvPostDetailTime.setText("Đăng lúc: " + post.getTimeAgo());
        binding.tvPostDetailStatus.setText("Trạng thái: " + post.getStatusLabel());

        if (post.getInterestTag() != null && !post.getInterestTag().isEmpty()) {
            binding.tvPostDetailTag.setVisibility(View.VISIBLE);
            binding.tvPostDetailTag.setText(post.getInterestTag());
        } else binding.tvPostDetailTag.setVisibility(View.GONE);

        if (post.getLocation() != null && !post.getLocation().isEmpty()) {
            binding.tvPostDetailLocation.setVisibility(View.VISIBLE);
            binding.tvPostDetailLocation.setText("Địa điểm: " + post.getLocation());
        } else binding.tvPostDetailLocation.setVisibility(View.GONE);

        binding.btnOpenGroupChat.setVisibility(post.isJoined() ? View.VISIBLE : View.GONE);
        if (post.isJoined()) binding.btnOpenGroupChat.setText("💬 Mở nhóm chat");
    }

    private void handlePendingApproval() {
        boolean showPending  = getIntent().getBooleanExtra("show_pending_actions", false);
        String pendingUserId = getIntent().getStringExtra("pending_user_uid");
        String pendingName   = getIntent().getStringExtra("pending_username");

        if (showPending && post != null && pendingUserId != null) {
            binding.layoutPendingApproval.setVisibility(View.VISIBLE);
            binding.tvPendingLabel.setText("👤 " + (pendingName != null ? pendingName : "Người dùng")
                    + " muốn tham gia hoạt động này");

            binding.btnApproveJoin.setOnClickListener(v -> approveUser(pendingUserId, pendingName));
            binding.btnRejectJoin.setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Xác nhận từ chối")
                    .setMessage("Bạn có chắc chắn muốn từ chối " + pendingName + "?")
                    .setPositiveButton("Từ chối", (d, w) -> rejectUser(pendingUserId, pendingName))
                    .setNegativeButton("Huỷ", null)
                    .show()
            );
        } else {
            if (binding.layoutPendingApproval != null) binding.layoutPendingApproval.setVisibility(View.GONE);
        }
    }

    private void approveUser(String memberId, String memberName) {
        if (post == null) return;
        String ownerId = FirebaseManager.getCurrentUserId();
        postRepo.approveMember(post.getId(), memberId, ownerId,
            new FirestorePostRepository.ActionCallback() {
                @Override public void onSuccess(String msg) {
                    runOnUiThread(() -> {
                        binding.layoutApproveReject.setVisibility(View.GONE);
                        binding.tvApprovalResult.setVisibility(View.VISIBLE);
                        binding.tvApprovalResult.setText("✅ Đã chấp nhận " + memberName);
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(PostDetailActivity.this)
                            .setTitle("Đã duyệt!")
                            .setMessage("Bạn đã duyệt " + memberName + " tham gia hoạt động.")
                            .setPositiveButton("OK", null)
                            .show();
                    });
                }
                @Override public void onError(String err) {
                    runOnUiThread(() ->
                        Toast.makeText(PostDetailActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show()
                    );
                }
            });
    }

    private void rejectUser(String memberId, String memberName) {
        if (post == null) return;
        String ownerId = FirebaseManager.getCurrentUserId();
        postRepo.rejectMember(post.getId(), memberId, ownerId,
            new FirestorePostRepository.ActionCallback() {
                @Override public void onSuccess(String msg) {
                    runOnUiThread(() -> {
                        binding.layoutApproveReject.setVisibility(View.GONE);
                        binding.tvApprovalResult.setVisibility(View.VISIBLE);
                        binding.tvApprovalResult.setText("❌ Đã từ chối " + memberName);
                    });
                }
                @Override public void onError(String err) {
                    runOnUiThread(() ->
                        Toast.makeText(PostDetailActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show()
                    );
                }
            });
    }

    private void openRelatedGroupChat() {
        if (post == null) return;
        chatRepo.getRoomByPostId(post.getId(), new FirestoreChatRepository.RoomCallback() {
            @Override public void onSuccess(Map<String, Object> room) {
                String roomId = (String) room.get("id");
                Intent intent = new Intent(PostDetailActivity.this, ConversationActivity.class);
                intent.putExtra("room_id", roomId);
                startActivity(intent);
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> 
                    Toast.makeText(PostDetailActivity.this,
                        "Không tìm thấy phòng chat cho hoạt động này", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
}
