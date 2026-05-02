package com.example.weconnect.presentation.ui;
import com.example.weconnect.presentation.ui.*;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.presentation.adapter.PostAdapter;
import com.example.weconnect.presentation.adapter.UserReviewAdapter;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestorePostRepository;
import com.example.weconnect.data.repository.FirestoreUserRepository;
import com.example.weconnect.data.repository.FirebaseFriendService;

import com.example.weconnect.domain.model.UserProfile;
import com.example.weconnect.domain.model.Post;
import com.example.weconnect.domain.model.UserReview;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserProfileActivity extends AppCompatActivity {

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    
    
    
    

    
    
    

    
    
    

    // Related posts (from other users matching interest tags)
    
    
    

    private String viewedUserUid = null; // Firebase UID của user đang xem

    private FirestorePostRepository postRepo;
    private FirestoreUserRepository userRepo;
    private FirebaseFriendService friendService;
    private ActivityResultLauncher<Intent> createPostLauncher;

    private com.example.weconnect.databinding.ActivityUserProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = com.example.weconnect.databinding.ActivityUserProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        postRepo    = new FirestorePostRepository();
        userRepo    = new FirestoreUserRepository();
        friendService = new FirebaseFriendService();

        setupCreatePostLauncher();
        
        bindUserProfile();
        setupClickListeners();
        bindSocialState();
        setupDrawerMenu();
        setupProfileTabs();
        bindActivePosts();
        loadMyActivities();
        hideRelatedPosts();
    }

    private void setupCreatePostLauncher() {
        createPostLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String content  = data.getStringExtra("post_content");
                        String tag      = data.getStringExtra("post_tag");
                        String location = data.getStringExtra("post_location");
                        int maxMembers  = data.getIntExtra("post_max_members", 10);
                        String imageUri = data.getStringExtra("post_image_uri");
                        long endTimeMs  = data.getLongExtra("post_end_time",
                                System.currentTimeMillis() + 24L * 60L * 60L * 1000L);
                        createPostViaFirebase(content, tag, location, maxMembers, imageUri, endTimeMs);
                    }
                }
        );
    }

    /** Tạo post mới trực tiếp vào Firestore (có kèm upload ảnh Firebase Storage nếu có) */
    private void createPostViaFirebase(String content, String tag, String location,
                                       int maxMembers, String imageUri, long endTimeMs) {
        String uid  = FirebaseManager.getCurrentUserId();
        String name = FirebaseManager.getUserName(this);
        if (uid == null) { Toast.makeText(this, "Chưa đăng nhập", Toast.LENGTH_SHORT).show(); return; }

        com.google.firebase.Timestamp endTs =
            new com.google.firebase.Timestamp(new Date(endTimeMs));

        if (imageUri != null && !imageUri.startsWith("http")) {
            // Upload ảnh lên Firebase Storage trước
            android.net.Uri uri = android.net.Uri.parse(imageUri);
            String path = "post_images/" + uid + "_" + System.currentTimeMillis() + ".jpg";
            com.google.firebase.storage.FirebaseStorage.getInstance()
                .getReference(path).putFile(uri)
                .addOnSuccessListener(snap -> snap.getStorage().getDownloadUrl()
                    .addOnSuccessListener(downloadUri -> postRepo.createPost(
                        uid, name != null ? name : "User", null,
                        content, tag, location, downloadUri.toString(),
                        maxMembers, null, endTs, createPostCallback())))
                .addOnFailureListener(e -> postRepo.createPost(
                    uid, name != null ? name : "User", null,
                    content, tag, location, null,
                    maxMembers, null, endTs, createPostCallback()));
        } else {
            postRepo.createPost(uid, name != null ? name : "User", null,
                content, tag, location, imageUri,
                maxMembers, null, endTs, createPostCallback());
        }
    }

    private FirestorePostRepository.ActionCallback createPostCallback() {
        return new FirestorePostRepository.ActionCallback() {
            @Override public void onSuccess(String msg) {
                runOnUiThread(() -> {
                    Toast.makeText(UserProfileActivity.this, "Đã tạo bài đăng!", Toast.LENGTH_SHORT).show();
                    bindActivePosts();
                });
            }
            @Override public void onError(String err) {
                runOnUiThread(() ->
                    Toast.makeText(UserProfileActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show());
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindSocialState();
        bindActivePosts();
        loadMyActivities();
        hideRelatedPosts();
        // Refresh profile sau khi edit
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        if (!viewOther) loadProfileFromFirestore(FirebaseManager.getCurrentUserId());
    }


    private void setupProfileTabs() {
        com.google.android.material.tabs.TabLayout tabLayout = binding.tabLayoutProfile;
        LinearLayout containerMyPosts = binding.containerMyPosts;
        LinearLayout containerMyActivities = binding.containerMyActivities;

        if (tabLayout == null) return;

        tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // Bài viết của tôi
                    if (containerMyPosts != null) containerMyPosts.setVisibility(View.VISIBLE);
                    if (containerMyActivities != null) containerMyActivities.setVisibility(View.GONE);
                } else {
                    // Hoạt động của tôi
                    if (containerMyPosts != null) containerMyPosts.setVisibility(View.GONE);
                    if (containerMyActivities != null) containerMyActivities.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
    }

    private void setupClickListeners() {
        binding.ivBackUserProfile.setOnClickListener(v -> finish());

        binding.btnViewArchive.setOnClickListener(v -> {
            String uid = viewedUserUid != null ? viewedUserUid : FirebaseManager.getCurrentUserId();
            Intent intent = new Intent(this, ArchivePostsActivity.class);
            intent.putExtra("username", binding.tvUserProfileName.getText().toString());
            if (uid != null) intent.putExtra("user_uid", uid);
            startActivity(intent);
        });

        // Bottom navigation
        View btnHome = binding.btnHomeProfile;
        View btnMessages = binding.btnMessagesProfile;
        View btnNotifications = binding.btnNotificationsProfile;

        if (btnHome != null) btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        if (btnMessages != null) btnMessages.setOnClickListener(v ->
            startActivity(new Intent(this, ChatListActivity.class)));
        if (btnNotifications != null) btnNotifications.setOnClickListener(v ->
            startActivity(new Intent(this, NotificationsActivity.class)));
    }

    private void setupDrawerMenu() {
        binding.drawerLayoutProfile.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        binding.ivMenuProfile.setOnClickListener(v ->
                binding.drawerLayoutProfile.openDrawer(Gravity.END)
        );

        binding.menuEditProfile.setOnClickListener(v -> {
            binding.drawerLayoutProfile.closeDrawer(Gravity.END);
            startActivity(new Intent(this, EditProfileActivity.class));
        });

        binding.menuChangePassword.setOnClickListener(v -> {
            binding.drawerLayoutProfile.closeDrawer(Gravity.END);
            startActivity(new Intent(this, ChangePasswordActivity.class));
        });

        binding.menuDeleteAccount.setOnClickListener(v -> {
            binding.drawerLayoutProfile.closeDrawer(Gravity.END);
            showDeleteAccountDialog();
        });

        LinearLayout menuLogout = binding.menuLogout;
        menuLogout.setOnClickListener(v -> {
            binding.drawerLayoutProfile.closeDrawer(Gravity.END);
            new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc muốn đăng xuất?")
                    .setPositiveButton("Đăng xuất", (d, w) -> {
                        // Clear session Firebase
                        FirebaseManager.clearSession(this);
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Huỷ", null)
                    .show();
        });

        LinearLayout menuBlockedUsers = binding.menuBlockedUsers;
        menuBlockedUsers.setOnClickListener(v -> {
            binding.drawerLayoutProfile.closeDrawer(Gravity.END);
            startActivity(new Intent(this, BlockedUsersActivity.class));
        });

        LinearLayout menuLegal = binding.menuLegal;
        menuLegal.setOnClickListener(v -> {
            binding.drawerLayoutProfile.closeDrawer(Gravity.END);
            startActivity(new Intent(this, LegalActivity.class));
        });
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Xoà tài khoản")
                .setMessage("Bạn có chắc chẫn muốn xoà tài khoản? Hành động này không thể hoàn tác.")
                .setPositiveButton("Xoà", (dialog, which) -> {
                    String uid = FirebaseManager.getCurrentUserId();
                    if (uid == null) return;
                    // Xoà Firestore data
                    userRepo.deleteAccount(uid, new FirestoreUserRepository.ActionCallback() {
                        @Override public void onSuccess(String msg) {
                            // Xoà Firebase Auth user
                            com.google.firebase.auth.FirebaseAuth.getInstance()
                                .getCurrentUser().delete()
                                .addOnCompleteListener(task -> runOnUiThread(() -> {
                                    FirebaseManager.clearSession(UserProfileActivity.this);
                                    Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                }));
                        }
                        @Override public void onError(String err) {
                            runOnUiThread(() -> Toast.makeText(UserProfileActivity.this,
                                    "Không thể xoà tài khoản.", Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    /** [FIREBASE] Điểm vào cho việc load profile — thấy intent UID hay dùng UID của mình */
    private void bindUserProfile() {
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        String intentUid  = getIntent().getStringExtra("user_uid");
        String myUid      = FirebaseManager.getCurrentUserId();

        if (viewOther && intentUid != null && !intentUid.isEmpty()) {
            viewedUserUid = intentUid;
        } else {
            viewedUserUid = myUid;
        }

        // Hiển thị tên tạm từ SharedPreferences trong khi chờ Firestore
        String tmpName = FirebaseManager.getUserName(this);
        binding.tvUserProfileName.setText(tmpName != null ? tmpName : "WeConnect User");
        binding.ivUserProfileAvatar.setImageResource(R.drawable.ic_user_placeholder);
        binding.tvUserReputation.setText("0");

        binding.rvUserReviews.setLayoutManager(new LinearLayoutManager(this));
        loadProfileFromFirestore(viewedUserUid);
        loadReviewsFromFirestore(viewedUserUid);
        loadInterestsFromFirestore(viewedUserUid);
    }

    /** Load profile từ Firestore users/{uid} */
    private void loadProfileFromFirestore(String uid) {
        if (uid == null) return;
        userRepo.getUserProfile(uid, new FirestoreUserRepository.UserCallback() {
            @Override public void onSuccess(Map<String, Object> profile) {
                runOnUiThread(() -> {
                    // Tên
                    String fullName = profile.get("fullName") != null ? profile.get("fullName").toString() : null;
                    if (fullName != null && !fullName.isEmpty()) {
                        binding.tvUserProfileName.setText(fullName);
                        FirebaseManager.saveUserName(UserProfileActivity.this, fullName);
                    }
                    // Bio
                    String bio = profile.get("bio") != null ? profile.get("bio").toString() : "";
                    if (binding.tvUserBio != null) {
                        binding.tvUserBio.setText(bio);
                        binding.tvUserBio.setVisibility(bio.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    // Gender
                    String gender = profile.get("gender") != null ? profile.get("gender").toString() : "";
                    if (binding.tvUserGender != null) {
                        binding.tvUserGender.setText(gender.isEmpty() ? "" : "Giới tính: " + gender);
                        binding.tvUserGender.setVisibility(gender.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    // Birthday
                    String birthday = profile.get("birthday") != null ? profile.get("birthday").toString() : "";
                    if (binding.tvUserBirthday != null) {
                        binding.tvUserBirthday.setText(birthday.isEmpty() ? "" : "🎂 " + birthday);
                        binding.tvUserBirthday.setVisibility(birthday.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    // Reputation
                    Object repObj = profile.get("reputationScore");
                    if (repObj instanceof Number && binding.tvUserReputation != null) {
                        binding.tvUserReputation.setText(String.valueOf(((Number) repObj).intValue()));
                    }
                    // Avatar
                    String avatarUrl = profile.get("avatarUrl") != null ? profile.get("avatarUrl").toString() : null;
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        com.bumptech.glide.Glide.with(UserProfileActivity.this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_user_placeholder)
                            .error(R.drawable.ic_user_placeholder)
                            .circleCrop()
                            .into(binding.ivUserProfileAvatar);
                    }
                });
            }
            @Override public void onError(String err) { /* giữ placeholder */ }
        });
    }

    /** Load reviews từ Firestore userReviews */

    /** Load interests từ Firestore users/{uid}.interestTags */
    private void loadInterestsFromFirestore(String uid) {
        if (uid == null) return;
        userRepo.getInterests(uid, new FirestoreUserRepository.InterestsCallback() {
            @Override public void onSuccess(java.util.List<String> interests) {
                // Có thể fallback từ SharedPreferences nếu rỗng
                if (interests.isEmpty()) {
                    android.content.SharedPreferences prefs =
                        getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
                    String saved = prefs.getString("user_interests", "");
                    if (!saved.isEmpty())
                        interests = java.util.Arrays.asList(saved.split(","));
                }
                displayInterests(interests);
            }
            @Override public void onError(String err) {
                // Fallback SharedPreferences
                android.content.SharedPreferences prefs =
                    getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
                String saved = prefs.getString("user_interests", "");
                if (!saved.isEmpty())
                    displayInterests(java.util.Arrays.asList(saved.split(",")));
            }
        });
    }

    // Legacy methods removed — replaced by loadProfileFromFirestore() / loadInterestsFromFirestore()
    private void loadInterestsFromLocal() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
        String saved = prefs.getString("user_interests", "");
        if (!saved.isEmpty()) {
            displayInterests(java.util.Arrays.asList(saved.split(",")));
        } else {
            // Final fallback
            List<String> defaultTags = new ArrayList<>();
            defaultTags.add("☕ Cà phê");
            defaultTags.add("💬 Giao lưu");
            displayInterests(defaultTags);
        }
    }

    private void displayInterests(List<String> interestTags) {
        binding.chipGroupUserInterests.removeAllViews();
        for (String tag : interestTags) {
            String trimmed = tag.trim();
            if (trimmed.isEmpty()) continue;
            Chip chip = new Chip(this);
            chip.setText(trimmed);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setFocusable(false);
            chip.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2);
            chip.setChipBackgroundColorResource(R.color.chip_background_state);
            chip.setTextColor(getResources().getColorStateList(R.color.chip_text_state, getTheme()));
            chip.setChipCornerRadius(getResources().getDimension(R.dimen.profile_interest_chip_radius));
            chip.setChipStrokeWidth(0f);
            binding.chipGroupUserInterests.addView(chip);
        }
    }

    /** [FIREBASE] Load bài viết của user từ Firestore */
    private void bindActivePosts() {
        String uid = viewedUserUid != null ? viewedUserUid : FirebaseManager.getCurrentUserId();
        if (uid == null) { showActivePosts(new ArrayList<>()); return; }
        postRepo.getUserPosts(uid, new FirestorePostRepository.PostsCallback() {
            @Override public void onSuccess(java.util.List<Map<String, Object>> posts) {
                java.util.List<Post> result = new java.util.ArrayList<>();
                for (Map<String, Object> p : posts) {
                    Post post = mapToPost(p);
                    if (post != null) result.add(post);
                }
                runOnUiThread(() -> showActivePosts(result));
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> showActivePosts(new ArrayList<>()));
            }
        });
    }

    private void showActivePosts(List<Post> activePosts) {
        if (activePosts.isEmpty()) {
            binding.tvNoActivePosts.setVisibility(View.VISIBLE);
            binding.rvActivePostsProfile.setVisibility(View.GONE);
        } else {
            binding.tvNoActivePosts.setVisibility(View.GONE);
            binding.rvActivePostsProfile.setVisibility(View.VISIBLE);
            binding.rvActivePostsProfile.setLayoutManager(new LinearLayoutManager(this));

            // Nếu xem profile người khác → truyền viewer interests để kiểm soát nút tham gia
            boolean viewOther = getIntent().getBooleanExtra("view_other", false);
            if (viewOther) {
                java.util.Set<String> myInterests = getMyInterestSet();
                binding.rvActivePostsProfile.setAdapter(new PostAdapter(this, activePosts, myInterests));
            } else {
                binding.rvActivePostsProfile.setAdapter(new PostAdapter(this, activePosts));
            }
        }
    }

    /** [FIREBASE] Load hoạt động đã tham gia từ Firestore */
    private void loadMyActivities() {
        String uid = viewedUserUid != null ? viewedUserUid : FirebaseManager.getCurrentUserId();
        if (uid == null) { showMyActivities(new ArrayList<>()); return; }
        postRepo.getMyActivities(uid, new FirestorePostRepository.PostsCallback() {
            @Override public void onSuccess(java.util.List<Map<String, Object>> posts) {
                java.util.List<Post> result = new java.util.ArrayList<>();
                for (Map<String, Object> p : posts) {
                    Post post = mapToPost(p);
                    if (post != null) result.add(post);
                }
                runOnUiThread(() -> showMyActivities(result));
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> showMyActivities(new ArrayList<>()));
            }
        });
    }

    private void showMyActivities(List<Post> activities) {
        View tvEmpty = binding.tvNoMyActivities;
        RecyclerView rv = binding.rvMyActivities;

        if (activities.isEmpty()) {
            if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            if (rv != null) rv.setVisibility(View.GONE);
        } else {
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            if (rv != null) {
                rv.setVisibility(View.VISIBLE);
                rv.setLayoutManager(new LinearLayoutManager(this));
                // Truyền viewer interests để PostAdapter quyết định CTA đúng
                boolean viewOther = getIntent().getBooleanExtra("view_other", false);
                if (viewOther) {
                    java.util.Set<String> myInterests = getMyInterestSet();
                    rv.setAdapter(new PostAdapter(this, activities, myInterests));
                } else {
                    rv.setAdapter(new PostAdapter(this, activities));
                }
            }
        }
    }

    /**
     * Lấy set sở thích của người đang đăng nhập (lowercase) từ SharedPreferences.
     */
    private java.util.Set<String> getMyInterestSet() {
        android.content.SharedPreferences prefs = getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
        String saved = prefs.getString("user_interests", "");
        java.util.Set<String> set = new java.util.HashSet<>();
        if (!saved.isEmpty()) {
            for (String tag : saved.split(",")) {
                String trimmed = tag.trim().toLowerCase();
                if (!trimmed.isEmpty()) set.add(trimmed);
            }
        }
        return set;
    }

    private Post mapToPost(Map<String, Object> p) {
        try {
            String id = (String) p.get("id");
            String authorName = (String) p.get("authorName");
            String content = (String) p.get("content");
            String tag = (String) p.get("interestTag");
            String location = (String) p.get("location");
            String imageUrl = (String) p.get("imageUrl");
            String authorAvatarUrl = (String) p.get("authorAvatarUrl");
            int maxMembers = p.get("maxMembers") instanceof Number ? ((Number) p.get("maxMembers")).intValue() : 10;
            int memberCount = p.get("memberCount") instanceof Number ? ((Number) p.get("memberCount")).intValue() : 1;
            boolean archived = Boolean.TRUE.equals(p.get("archived"));

            com.google.firebase.Timestamp endTs  = (com.google.firebase.Timestamp) p.get("endTime");
            com.google.firebase.Timestamp startTs = (com.google.firebase.Timestamp) p.get("startTime");
            long endMillis   = endTs != null ? endTs.toDate().getTime() : System.currentTimeMillis() + 86400000L;
            long startMillis = startTs != null ? startTs.toDate().getTime() : System.currentTimeMillis();

            String currentUid = FirebaseManager.getCurrentUserId();
            String authorId = (String) p.get("authorId");
            boolean isMyPost = currentUid != null && currentUid.equals(authorId);

            Post post = new Post(id, authorName, "vừa xong", content, tag, location,
                    0, 0, memberCount, 0, 0, maxMembers, isMyPost,
                    startMillis, endMillis, archived);
            post.setAuthorId(authorId != null ? authorId.hashCode() : 0);
            post.setAuthorUid(authorId);
            post.setAvatarUrl(authorAvatarUrl);
            if (imageUrl != null && !imageUrl.isEmpty()) post.setPostImageUri(imageUrl);
            return post;
        } catch (Exception e) {
            android.util.Log.e("UserProfile", "mapToPost error: " + e.getMessage());
            return null;
        }
    }

    /**
     * [FIREBASE] Load bài viết gợi ý dựa trên sở thích chung.
     */
    private void loadRelatedPosts() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) { hideRelatedPosts(); return; }

        userRepo.getInterests(uid, new FirestoreUserRepository.InterestsCallback() {
            @Override public void onSuccess(java.util.List<String> interests) {
                if (interests == null || interests.isEmpty()) { runOnUiThread(() -> hideRelatedPosts()); return; }
                fetchAndFilterRelatedPosts(interests);
            }
            @Override public void onError(String err) { runOnUiThread(() -> hideRelatedPosts()); }
        });
    }

    private void fetchAndFilterRelatedPosts(java.util.List<String> userInterests) {
        java.util.Set<String> interestSet = new java.util.HashSet<>();
        for (String tag : userInterests) {
            String t = tag.trim();
            if (!t.isEmpty()) interestSet.add(t.toLowerCase());
        }
        if (interestSet.isEmpty()) { runOnUiThread(() -> hideRelatedPosts()); return; }

        String myUid = FirebaseManager.getCurrentUserId();
        postRepo.getActivePosts(new FirestorePostRepository.PostsCallback() {
            @Override public void onSuccess(java.util.List<Map<String, Object>> posts) {
                java.util.List<Post> related = new java.util.ArrayList<>();
                for (Map<String, Object> p : posts) {
                    String authorUid = p.get("authorUid") != null ? p.get("authorUid").toString() : null;
                    if (myUid != null && myUid.equals(authorUid)) continue;
                    if (viewedUserUid != null && viewedUserUid.equals(authorUid)) continue;
                    String tag = p.get("interestTag") != null ? p.get("interestTag").toString().trim().toLowerCase() : "";
                    if (interestSet.contains(tag)) {
                        Post post = mapToPost(p);
                        if (post != null) related.add(post);
                    }
                }
                runOnUiThread(() -> showRelatedPosts(related));
            }
            @Override public void onError(String err) { runOnUiThread(() -> hideRelatedPosts()); }
        });
    }

    /**
     * [FIREBASE] loadSuggestedUsers — gợi ý user có sở thích chung.
     * Hiện tại disabled (tạm ẩn), chỉ để compile clean.
     */
    private void loadSuggestedUsers() {
        // Disabled — feature sẽ được implement sau khi có Firestore index cho interests
    }

    private void showRelatedPosts(java.util.List<Post> relatedPosts) {
        if (relatedPosts.isEmpty()) {
            hideRelatedPosts();
            return;
        }
        binding.tvRelatedPostsTitle.setVisibility(View.VISIBLE);
        binding.tvNoRelatedPosts.setVisibility(View.GONE);
        binding.rvRelatedPosts.setVisibility(View.VISIBLE);
        binding.rvRelatedPosts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRelatedPosts.setAdapter(new PostAdapter(this, relatedPosts));
    }

    private void hideRelatedPosts() {
        binding.tvRelatedPostsTitle.setVisibility(View.GONE);
        binding.tvNoRelatedPosts.setVisibility(View.GONE);
        binding.rvRelatedPosts.setVisibility(View.GONE);
    }

    private void showSuggestedUsersUI(java.util.List<Map<String, Object>> suggestions) {
        try {
            // Tìm vị trí chèn (sau phần related posts, trước reviews)
            LinearLayout mainContent = (LinearLayout) binding.tvRelatedPostsTitle.getParent();
            if (mainContent == null) return;

            // Xóa phần gợi ý cũ nếu đã có (tránh trùng lặp khi onResume)
            View existingTitle = mainContent.findViewWithTag("suggest_title");
            View existingScroll = mainContent.findViewWithTag("suggest_scroll");
            if (existingTitle != null) mainContent.removeView(existingTitle);
            if (existingScroll != null) mainContent.removeView(existingScroll);

            // Tạo tiêu đề "Gợi ý cho bạn"
            TextView tvSuggestTitle = new TextView(this);
            tvSuggestTitle.setTag("suggest_title");
            tvSuggestTitle.setText("👤 Gợi ý cho bạn");
            tvSuggestTitle.setTextSize(18);
            tvSuggestTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvSuggestTitle.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            tvSuggestTitle.setPadding(48, 32, 48, 16);

            // Tạo HorizontalScrollView chứa danh sách user
            android.widget.HorizontalScrollView scrollView = new android.widget.HorizontalScrollView(this);
            scrollView.setTag("suggest_scroll");
            scrollView.setHorizontalScrollBarEnabled(false);
            scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

            LinearLayout userRow = new LinearLayout(this);
            userRow.setOrientation(LinearLayout.HORIZONTAL);
            userRow.setPadding(32, 8, 32, 24);

            float density = getResources().getDisplayMetrics().density;

            for (Map<String, Object> item : suggestions) {
                String name = item.get("fullName") != null ? item.get("fullName").toString() : "Người dùng";
                long userId = -1;
                try {
                    if (item.get("id") != null) userId = ((Number) item.get("id")).longValue();
                } catch (Exception ignored) {}

                // Card cho mỗi user
                com.google.android.material.card.MaterialCardView card =
                        new com.google.android.material.card.MaterialCardView(this);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        (int) (140 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(12, 0, 12, 0);
                card.setLayoutParams(cardParams);
                card.setRadius(24f);
                card.setCardElevation(4f);
                card.setCardBackgroundColor(getResources().getColor(R.color.card_surface, getTheme()));

                LinearLayout cardContent = new LinearLayout(this);
                cardContent.setOrientation(LinearLayout.VERTICAL);
                cardContent.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                cardContent.setPadding(24, 28, 24, 24);

                // Avatar
                ImageView avatar = new ImageView(this);
                avatar.setImageResource(R.drawable.ic_user_placeholder);
                LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(160, 160);
                avatar.setLayoutParams(avatarParams);
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cardContent.addView(avatar);

                // Tên
                TextView tvName = new TextView(this);
                tvName.setText(name);
                tvName.setTextSize(13);
                tvName.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                tvName.setGravity(android.view.Gravity.CENTER);
                tvName.setMaxLines(2);
                tvName.setPadding(0, 12, 0, 8);
                cardContent.addView(tvName);

                // Nút "Thêm bạn bè" - dùng Button thường thay vì MaterialButton
                android.widget.Button btnAdd = new android.widget.Button(this);
                btnAdd.setText("+ Thêm");
                btnAdd.setTextSize(11);
                btnAdd.setAllCaps(false);
                btnAdd.setTextColor(0xFFFFFFFF);
                btnAdd.setBackgroundResource(R.drawable.bg_selected_tag);
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int) (36 * density));
                btnAdd.setLayoutParams(btnParams);
                btnAdd.setPadding(0, 0, 0, 0);

                final String friendName = name;
                btnAdd.setOnClickListener(v -> {
                    // Fake request
                    btnAdd.setText("Đã gửi");
                    btnAdd.setEnabled(false);
                    btnAdd.setAlpha(0.6f);
                    Toast.makeText(this, "Đã gửi lời mời kết bạn tới " + friendName, Toast.LENGTH_SHORT).show();
                });
                cardContent.addView(btnAdd);

                card.addView(cardContent);

                // Click vào card -> xem profile
                final long fUserId = userId;
                card.setOnClickListener(v -> {
                    Intent intent = new Intent(this, UserProfileActivity.class);
                    intent.putExtra("username", friendName);
                    if (fUserId > 0) intent.putExtra("user_id", fUserId);
                    intent.putExtra("view_other", true);
                    startActivity(intent);
                });

                userRow.addView(card);
            }

            scrollView.addView(userRow);

            // Chèn vào layout (sau related posts)
            int insertIndex = mainContent.indexOfChild(binding.rvRelatedPosts);
            if (insertIndex >= 0) {
                mainContent.addView(tvSuggestTitle, insertIndex + 1);
                mainContent.addView(scrollView, insertIndex + 2);
            } else {
                mainContent.addView(tvSuggestTitle);
                mainContent.addView(scrollView);
            }
        } catch (Exception e) {
            // Tránh crash nếu có lỗi UI
            e.printStackTrace();
        }
    }

    private void bindSocialState() {
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        String myUid = FirebaseManager.getCurrentUserId();
        boolean isOwnProfile = !viewOther || viewedUserUid == null
                || (myUid != null && myUid.equals(viewedUserUid));

        if (isOwnProfile) {
            binding.ivBackUserProfile.setVisibility(View.GONE);
            binding.ivMenuProfile.setVisibility(View.VISIBLE);
            binding.tvFriendCount.setVisibility(View.VISIBLE);
            binding.layoutSocialButtons.setVisibility(View.GONE);
            binding.layoutRateReport.setVisibility(View.GONE);
            binding.btnViewArchive.setVisibility(View.VISIBLE);
            binding.footerNavigationProfile.setVisibility(View.VISIBLE);

            binding.ivUserProfileAvatar.setOnClickListener(v -> showAvatarOptionsSheet());
            loadSavedAvatar();
            binding.cardCreatePostProfile.setVisibility(View.VISIBLE);
            binding.cardCreatePostProfile.setOnClickListener(v -> createPostLauncher.launch(
                    new Intent(this, CreatePostActivity.class)));

            binding.tvInterestsTitle.setVisibility(View.VISIBLE);
            binding.chipGroupUserInterests.setVisibility(View.VISIBLE);
            binding.rvActivePostsProfile.setVisibility(View.VISIBLE);

            loadFriendCountFromFirebase(myUid);
            return;
        }

        // Hồ sơ người khác
        binding.ivBackUserProfile.setVisibility(View.VISIBLE);
        binding.ivMenuProfile.setVisibility(View.VISIBLE);
        binding.tvFriendCount.setVisibility(View.GONE);
        binding.btnViewArchive.setVisibility(View.GONE);
        binding.layoutSocialButtons.setVisibility(View.VISIBLE);
        binding.layoutRateReport.setVisibility(View.VISIBLE);
        if (binding.btnReportUser != null) binding.btnReportUser.setVisibility(View.GONE);
        binding.footerNavigationProfile.setVisibility(View.GONE);
        binding.cardCreatePostProfile.setVisibility(View.GONE);

        binding.tvInterestsTitle.setVisibility(View.VISIBLE);
        binding.chipGroupUserInterests.setVisibility(View.VISIBLE);
        binding.rvActivePostsProfile.setVisibility(View.VISIBLE);
        binding.tvReviewsTitle.setVisibility(View.VISIBLE);
        binding.rvUserReviews.setVisibility(View.VISIBLE);

        binding.btnRateUser.setOnClickListener(v -> showRateUserDialog());

        binding.ivMenuProfile.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(this, binding.ivMenuProfile);
            popup.getMenu().add(0, 1, 0, "🚫 Chặn người dùng");
            popup.getMenu().add(0, 2, 1, "⚠️ Báo cáo");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) { showBlockUserConfirmDialog(); return true; }
                if (item.getItemId() == 2) { showReportUserDialog(); return true; }
                return false;
            });
            popup.show();
        });

        binding.ivUserProfileAvatar.setOnClickListener(null);
        binding.ivUserProfileAvatar.setClickable(false);

        if (myUid != null && viewedUserUid != null) {
            friendService.getFriendStatus(myUid, viewedUserUid, status -> runOnUiThread(() -> setupFriendButton(status)));
        } else {
            setupFriendButton("NONE");
        }
    }

    private void loadFriendCountFromFirebase(String uid) {
        if (uid == null) { binding.tvFriendCount.setText("👥 Bạn bè: 0"); return; }
        friendService.getFriendCount(uid, count -> runOnUiThread(() -> {
            binding.tvFriendCount.setText("👥 Bạn bè: " + count);
            binding.tvFriendCount.setOnClickListener(v -> showFriendListDialog());
        }));
    }

    // loadFriendStatusFromApi and lookupUserIdByName replaced by Firebase getFriendStatus in bindSocialState
    // These stubs are kept to avoid any remaining references compiling cleanly
    @SuppressWarnings("unused")
    private void loadFriendStatusFromApi(long ignored) { setupFriendButton("NONE"); }
    @SuppressWarnings("unused")
    private void lookupUserIdByName(String name) { setupFriendButton("NONE"); }

    private void setupFriendButton(String status) {
        switch (status) {
            case "BLOCKED":
                binding.btnAddFriend.setText("Đã chặn");
                binding.btnAddFriend.setEnabled(false);
                binding.btnAddFriend.setAlpha(0.5f);
                binding.btnMessage.setVisibility(View.GONE);
                binding.tvInterestsTitle.setVisibility(View.GONE);
                binding.chipGroupUserInterests.setVisibility(View.GONE);
                binding.rvActivePostsProfile.setVisibility(View.GONE);
                binding.tvNoActivePosts.setVisibility(View.GONE);
                binding.tvReviewsTitle.setVisibility(View.GONE);
                binding.rvUserReviews.setVisibility(View.GONE);
                break;

            case "FRIEND":
                binding.btnAddFriend.setText("Bạn bè");
                binding.btnAddFriend.setEnabled(true);
                binding.btnAddFriend.setAlpha(1.0f);
                binding.btnMessage.setVisibility(View.VISIBLE);
                binding.btnAddFriend.setOnClickListener(v -> showFriendOptionsMenu());
                binding.btnMessage.setOnClickListener(v -> {
                    if (viewedUserUid == null) {
                        Toast.makeText(this, "Không thể nhắn tin", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String myUid = FirebaseManager.getCurrentUserId();
                    String chatName = binding.tvUserProfileName.getText().toString();
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("chatRooms")
                        .whereEqualTo("type", "direct")
                        .whereArrayContains("memberIds", myUid)
                        .get()
                        .addOnSuccessListener(snaps -> {
                            String roomId = null;
                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snaps) {
                                Object members = doc.get("memberIds");
                                if (members instanceof java.util.List
                                        && ((java.util.List<?>) members).contains(viewedUserUid)) {
                                    roomId = doc.getId();
                                    break;
                                }
                            }
                            if (roomId != null) {
                                Intent intent = new Intent(UserProfileActivity.this, ConversationActivity.class);
                                intent.putExtra("room_id", roomId);
                                intent.putExtra("chat_name", chatName);
                                startActivity(intent);
                            } else {
                                java.util.Map<String, Object> room = new java.util.HashMap<>();
                                room.put("type", "direct");
                                room.put("memberIds", java.util.Arrays.asList(myUid, viewedUserUid));
                                room.put("title", chatName);
                                room.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("chatRooms").add(room)
                                    .addOnSuccessListener(ref -> {
                                        Intent intent = new Intent(UserProfileActivity.this, ConversationActivity.class);
                                        intent.putExtra("room_id", ref.getId());
                                        intent.putExtra("chat_name", chatName);
                                        startActivity(intent);
                                    });
                            }
                        });
                });
                break;

            case "PENDING_SENT":
                binding.btnAddFriend.setText("Đã gửi lời mời");
                binding.btnAddFriend.setEnabled(true);
                binding.btnAddFriend.setAlpha(0.8f);
                binding.btnMessage.setVisibility(View.GONE);
                binding.btnAddFriend.setOnClickListener(v -> {
                    String myUid = FirebaseManager.getCurrentUserId();
                    if (myUid == null || viewedUserUid == null) return;
                    friendService.cancelFriendRequest(myUid, viewedUserUid,
                        new FirebaseFriendService.ActionCallback() {
                            @Override public void onSuccess(String msg) {
                                runOnUiThread(() -> {
                                    Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                                    setupFriendButton("NONE");
                                });
                            }
                            @Override public void onError(String err) {
                                runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, err, Toast.LENGTH_SHORT).show());
                            }
                        });
                });
                break;

            case "PENDING_RECEIVED":
                binding.btnAddFriend.setText("Phản hồi");
                binding.btnAddFriend.setEnabled(true);
                binding.btnAddFriend.setAlpha(1.0f);
                binding.btnMessage.setVisibility(View.GONE);
                binding.btnAddFriend.setOnClickListener(v -> showFriendResponseDialog());
                break;

            default: // NONE
                binding.btnAddFriend.setText("+ Thêm bạn bè");
                binding.btnAddFriend.setEnabled(true);
                binding.btnAddFriend.setAlpha(1.0f);
                binding.btnMessage.setVisibility(View.GONE);
                binding.btnAddFriend.setOnClickListener(v -> {
                    String myUid = FirebaseManager.getCurrentUserId();
                    if (myUid == null || viewedUserUid == null) {
                        Toast.makeText(this, "Không thể thêm bạn bè", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    friendService.sendFriendRequest(myUid, viewedUserUid,
                        new FirebaseFriendService.ActionCallback() {
                            @Override public void onSuccess(String msg) {
                                runOnUiThread(() -> {
                                    Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                                    setupFriendButton("PENDING_SENT");
                                });
                            }
                            @Override public void onError(String err) {
                                runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, err, Toast.LENGTH_SHORT).show());
                            }
                        });
                });
                break;
        }
    }

    private void showFriendListDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(0, 0, 0, 48);

        // Header
        TextView header = new TextView(this);
        header.setText("👥 Danh sách bạn bè");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 48, 0, 24);
        root.addView(header);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFFE8E4DE);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div);

        // Loading indicator
    TextView tvLoading = new TextView(this);
    tvLoading.setText("Đang tải...");
    tvLoading.setTextSize(15);
    tvLoading.setTextColor(getResources().getColor(R.color.text_secondary, null));
    tvLoading.setGravity(Gravity.CENTER);
    tvLoading.setPadding(0, 48, 0, 48);
    root.addView(tvLoading);
    sheet.setContentView(root);
    sheet.show();

    // Load từ Firebase
    String uid = FirebaseManager.getCurrentUserId();
    if (uid == null) { root.removeView(tvLoading); return; }
    friendService.getFriends(uid, new FirebaseFriendService.FriendsCallback() {
        @Override public void onSuccess(java.util.List<java.util.Map<String, Object>> friendsList) {
            runOnUiThread(() -> {
                root.removeView(tvLoading);
                if (friendsList.isEmpty()) {
                    TextView tvEmpty = new TextView(UserProfileActivity.this);
                    tvEmpty.setText("Bạn chưa có bạn bè nào");
                    tvEmpty.setTextSize(15);
                    tvEmpty.setTextColor(getResources().getColor(R.color.text_secondary, null));
                    tvEmpty.setGravity(Gravity.CENTER);
                    tvEmpty.setPadding(0, 48, 0, 48);
                    root.addView(tvEmpty);
                } else {
                    for (java.util.Map<String, Object> friend : friendsList) {
                        String friendName = friend.get("fullName") != null
                                ? friend.get("fullName").toString() : "Người dùng";
                        String friendUid = friend.get("id") != null ? friend.get("id").toString() : null;

                        LinearLayout row = new LinearLayout(UserProfileActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(64, 32, 64, 32);
                        row.setBackgroundResource(android.R.drawable.list_selector_background);
                        row.setClickable(true);
                        row.setFocusable(true);

                        TextView tvIcon = new TextView(UserProfileActivity.this);
                        tvIcon.setText("\uD83D\uDC64");
                        tvIcon.setTextSize(22);
                        row.addView(tvIcon);

                        TextView tvName = new TextView(UserProfileActivity.this);
                        tvName.setText(friendName);
                        tvName.setTextSize(16);
                        tvName.setTextColor(getResources().getColor(R.color.text_primary, null));
                        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                        tvName.setPadding(32, 0, 0, 0);
                        row.addView(tvName);

                        final String fUid = friendUid;
                        final String fName = friendName;
                        row.setOnClickListener(v -> {
                            sheet.dismiss();
                            Intent intent = new Intent(UserProfileActivity.this, UserProfileActivity.class);
                            intent.putExtra("view_other", true);
                            if (fUid != null) intent.putExtra("user_uid", fUid);
                            startActivity(intent);
                        });
                        root.addView(row);

                        View sep = new View(UserProfileActivity.this);
                        sep.setBackgroundColor(0xFFE8E4DE);
                        sep.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 1));
                        root.addView(sep);
                    }
                }
            });
        }
        @Override public void onError(String err) {
            runOnUiThread(() -> tvLoading.setText("Lỗi: " + err));
        }
    });
    }

    private void showFriendOptionsMenu() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(0, 0, 0, 48);

        // Header
        android.widget.TextView header = new android.widget.TextView(this);
        header.setText("Tuỳ chọn bạn bè");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 48, 0, 24);
        root.addView(header);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFFE8E4DE);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div);

        // Unfriend option
        android.widget.LinearLayout unfriendRow = createOptionRow(
                "👋", "Huỷ kết bạn", "Xoá " + binding.tvUserProfileName.getText().toString() + " khỏi danh sách bạn bè",
                getResources().getColor(R.color.text_primary, null));
        unfriendRow.setOnClickListener(v -> {
            sheet.dismiss();
            String userName = binding.tvUserProfileName.getText().toString();
            new AlertDialog.Builder(this)
                    .setTitle("Huỷ kết bạn")
                    .setMessage("Bạn có chắc muốn huỷ kết bạn với " + userName + "?")
                    .setPositiveButton("Huỷ kết bạn", (d, w) -> {
                        String myUid = FirebaseManager.getCurrentUserId();
                        if (myUid != null && viewedUserUid != null) {
                            friendService.unfriend(myUid, viewedUserUid,
                                new FirebaseFriendService.ActionCallback() {
                                    @Override public void onSuccess(String msg) {
                                        runOnUiThread(() -> {
                                            Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                                            setupFriendButton("NONE");
                                        });
                                    }
                                    @Override public void onError(String err) {
                                        runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, err, Toast.LENGTH_SHORT).show());
                                    }
                                });
                        }
                    })
                    .setNegativeButton("Không", null)
                    .show();
        });
        root.addView(unfriendRow);

        // Divider
        View div2 = new View(this);
        div2.setBackgroundColor(0xFFE8E4DE);
        div2.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(div2);

        // Block option
        android.widget.LinearLayout blockRow = createOptionRow(
                "🚫", "Chặn người dùng", binding.tvUserProfileName.getText().toString() + " sẽ không thể liên hệ với bạn",
                0xFFFF4D6D);
        blockRow.setOnClickListener(v -> {
            sheet.dismiss();
            String userName = binding.tvUserProfileName.getText().toString();
            new AlertDialog.Builder(this)
                    .setTitle("Chặn người dùng")
                    .setMessage("Bạn có chắc muốn chặn " + userName + "? Người này sẽ không thể liên hệ với bạn.")
                    .setPositiveButton("Chặn", (d, w) -> {
                        String myUid = FirebaseManager.getCurrentUserId();
                        if (myUid != null && viewedUserUid != null) {
                            friendService.blockUser(myUid, viewedUserUid,
                                new FirebaseFriendService.ActionCallback() {
                                    @Override public void onSuccess(String msg) {
                                        runOnUiThread(() -> {
                                            Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                                            setupFriendButton("BLOCKED");
                                        });
                                    }
                                    @Override public void onError(String err) {
                                        runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, err, Toast.LENGTH_SHORT).show());
                                    }
                                });
                        }
                    })
                    .setNegativeButton("Không", null)
                    .show();
        });
        root.addView(blockRow);

        sheet.setContentView(root);
        sheet.show();
    }

    private android.widget.LinearLayout createOptionRow(String icon, String title, String subtitle, int titleColor) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(64, 36, 64, 36);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setClickable(true);

        android.widget.TextView tvIcon = new android.widget.TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(24);
        row.addView(tvIcon);

        android.widget.LinearLayout textCol = new android.widget.LinearLayout(this);
        textCol.setOrientation(android.widget.LinearLayout.VERTICAL);
        textCol.setPadding(32, 0, 0, 0);

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(titleColor);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvTitle);

        android.widget.TextView tvSubtitle = new android.widget.TextView(this);
        tvSubtitle.setText(subtitle);
        tvSubtitle.setTextSize(12);
        tvSubtitle.setTextColor(getResources().getColor(R.color.text_secondary, null));
        textCol.addView(tvSubtitle);

        row.addView(textCol);
        return row;
    }

    // [FIREBASE] Load common activities từ Firestore
    private void showRateUserDialog() {
        if (viewedUserUid == null) {
            Toast.makeText(this, "Không thể đánh giá người dùng này", Toast.LENGTH_SHORT).show();
            return;
        }
        String myUid = FirebaseManager.getCurrentUserId();
        if (myUid == null) return;
        Toast.makeText(this, "Đang tải hoạt động chung...", Toast.LENGTH_SHORT).show();

        FirebaseManager.getFirestore().collection("posts")
            .whereArrayContains("approvedMembers", myUid)
            .get()
            .addOnSuccessListener(snaps -> {
                java.util.List<String> activityNames = new java.util.ArrayList<>();
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snaps) {
                    Object members = doc.get("approvedMembers");
                    if (members instanceof java.util.List
                            && ((java.util.List<?>) members).contains(viewedUserUid)) {
                        String content = doc.getString("content");
                        String tag     = doc.getString("interestTag");
                        String display = (tag != null && !tag.isEmpty()) ? "[" + tag + "] " + content : content;
                        if (display != null && !display.isEmpty()) activityNames.add(display);
                    }
                }
                if (activityNames.isEmpty()) activityNames.add("Hoạt động chung");
                java.util.List<String> finalList = activityNames;
                runOnUiThread(() -> showRateUserDialogWithActivities(finalList));
            })
            .addOnFailureListener(e -> runOnUiThread(() -> {
                java.util.List<String> fallback = java.util.Arrays.asList("Hoạt động chung");
                showRateUserDialogWithActivities(fallback);
            }));
    }

    private void showRateUserDialogWithActivities(List<String> activityNames) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(64, 48, 64, 48);

        // Header
        TextView header = new TextView(this);
        header.setText("⭐ Đánh giá " + binding.tvUserProfileName.getText().toString());
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        // Activity selector label
        TextView actLabel = new TextView(this);
        actLabel.setText("Hoạt động chung:");
        actLabel.setTextSize(14);
        actLabel.setTextColor(getResources().getColor(R.color.text_secondary, null));
        LinearLayout.LayoutParams actLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actLabelParams.topMargin = 24;
        actLabel.setLayoutParams(actLabelParams);
        root.addView(actLabel);

        // Spinner for activity selection
        android.widget.Spinner spinnerActivity = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, activityNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerActivity.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spinnerParams.topMargin = 8;
        spinnerActivity.setLayoutParams(spinnerParams);
        root.addView(spinnerActivity);

        // Rating stars
        RatingBar ratingBar = new RatingBar(this, null, android.R.attr.ratingBarStyle);
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(0f);
        LinearLayout.LayoutParams ratingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ratingParams.gravity = Gravity.CENTER;
        ratingParams.topMargin = 24;
        ratingBar.setLayoutParams(ratingParams);
        root.addView(ratingBar);

        // Comment input
        EditText etComment = new EditText(this);
        etComment.setHint("Nhận xét (không bắt buộc)");
        etComment.setBackground(new ColorDrawable(Color.TRANSPARENT));
        etComment.setBackgroundResource(android.R.drawable.edit_text);
        etComment.setPadding(24, 24, 24, 24);
        etComment.setTextSize(14);
        etComment.setMinLines(2);
        LinearLayout.LayoutParams commentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        commentParams.topMargin = 24;
        etComment.setLayoutParams(commentParams);
        root.addView(etComment);

        // Submit button
        MaterialButton btnSubmit = new MaterialButton(this);
        btnSubmit.setText("Gửi đánh giá");
        btnSubmit.setAllCaps(false);
        btnSubmit.setCornerRadius(48);
        btnSubmit.setBackgroundTintList(getResources().getColorStateList(R.color.primary_pink, null));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120);
        btnParams.topMargin = 32;
        btnSubmit.setLayoutParams(btnParams);
        btnSubmit.setOnClickListener(v -> {
            if (ratingBar.getRating() == 0) {
                Toast.makeText(this, "Vui lòng chọn số sao", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedActivity = spinnerActivity.getSelectedItem().toString();
            sheet.dismiss();
            submitReviewToBackend((int) ratingBar.getRating(),
                    etComment.getText().toString().trim(), selectedActivity);
        });
        root.addView(btnSubmit);

        sheet.setContentView(root);
        sheet.show();
    }

    private void submitReviewToBackend(int stars, String comment, String activityName) {
        if (viewedUserUid == null) {
            Toast.makeText(this, "Không thể gửi đánh giá", Toast.LENGTH_SHORT).show();
            return;
        }
        String myUid  = FirebaseManager.getCurrentUserId();
        String myName = FirebaseManager.getUserName(this);
        if (myUid == null) return;

        String[] labels = {"Cần cải thiện", "Trung bình", "Tích cực", "Đáng tin cậy", "Xuất sắc"};
        String reputationLabel = labels[Math.min(stars - 1, labels.length - 1)];

        Map<String, Object> review = new HashMap<>();
        review.put("actorUid",        myUid);
        review.put("actorName",       myName != null ? myName : "Người dùng");
        review.put("reviewedUid",     viewedUserUid);
        review.put("activityName",    activityName);
        review.put("reputationLabel", reputationLabel);
        review.put("comment",         comment.isEmpty() ? "Đánh giá " + stars + " sao" : comment);
        review.put("rating",          stars);
        review.put("createdAt",       com.google.firebase.firestore.FieldValue.serverTimestamp());

        FirebaseManager.getFirestore()
            .collection("userReviews").add(review)
            .addOnSuccessListener(ref -> runOnUiThread(() -> {
                Toast.makeText(this, "Đã gửi đánh giá " + stars + " sao!", Toast.LENGTH_SHORT).show();
                loadReviewsFromFirestore(viewedUserUid);
            }))
            .addOnFailureListener(e -> runOnUiThread(() ->
                Toast.makeText(this, "Không thể gửi đánh giá: " + e.getMessage(), Toast.LENGTH_SHORT).show()));
    }

    private void loadReviewsFromBackend() {
        loadReviewsFromFirestore(viewedUserUid != null ? viewedUserUid : FirebaseManager.getCurrentUserId());
    }

    private void loadReviewsFromFirestore(String targetUid) {
        if (targetUid == null) {
            binding.rvUserReviews.setAdapter(new UserReviewAdapter(new ArrayList<>()));
            return;
        }
        FirebaseManager.getFirestore()
            .collection("userReviews")
            .whereEqualTo("reviewedUid", targetUid)
            .get()
            .addOnSuccessListener(snaps -> {
                List<UserReview> reviews = new ArrayList<>();
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snaps) {
                    String reviewerName    = doc.getString("actorName")       != null ? doc.getString("actorName")       : "Ẩn danh";
                    String activityName    = doc.getString("activityName")    != null ? doc.getString("activityName")    : "";
                    String reputationLabel = doc.getString("reputationLabel") != null ? doc.getString("reputationLabel") : "";
                    String reviewComment   = doc.getString("comment")         != null ? doc.getString("comment")         : "";
                    reviews.add(new UserReview(reviewerName, activityName, reputationLabel, reviewComment));
                }
                runOnUiThread(() -> binding.rvUserReviews.setAdapter(new UserReviewAdapter(reviews)));
            })
            .addOnFailureListener(e -> runOnUiThread(() ->
                binding.rvUserReviews.setAdapter(new UserReviewAdapter(new ArrayList<>()))));
    }

    private void showFriendResponseDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(0, 0, 0, 48);

        // Title
        TextView header = new TextView(this);
        header.setText("Phản hồi lời mời kết bạn");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 48, 0, 24);
        root.addView(header);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFFE8E4DE);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div);

        // Option: Xác nhận
        LinearLayout rowAccept = createOptionRow("✅", "Xác nhận",
                "Chấp nhận lời mời kết bạn", getResources().getColor(R.color.text_primary, null));
        rowAccept.setOnClickListener(v -> {
            sheet.dismiss();
            String myUid = FirebaseManager.getCurrentUserId();
            if (myUid != null && viewedUserUid != null) {
                friendService.acceptFriendRequest(viewedUserUid, myUid,
                    new FirebaseFriendService.ActionCallback() {
                        @Override public void onSuccess(String msg) {
                            runOnUiThread(() -> { Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show(); setupFriendButton("FRIEND"); });
                        }
                        @Override public void onError(String err) {
                            runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, err, Toast.LENGTH_SHORT).show());
                        }
                    });
            }
        });
        root.addView(rowAccept);

        // Option: Từ chối
        LinearLayout rowDecline = createOptionRow("❌", "Từ chối",
                "Từ chối lời mời kết bạn", getResources().getColor(R.color.danger_red, null));
        rowDecline.setOnClickListener(v -> {
            sheet.dismiss();
            String myUid = FirebaseManager.getCurrentUserId();
            if (myUid != null && viewedUserUid != null) {
                friendService.declineFriendRequest(viewedUserUid, myUid,
                    new FirebaseFriendService.ActionCallback() {
                        @Override public void onSuccess(String msg) {
                            runOnUiThread(() -> { Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show(); setupFriendButton("NONE"); });
                        }
                        @Override public void onError(String err) {
                            runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, err, Toast.LENGTH_SHORT).show());
                        }
                    });
            }
        });
        root.addView(rowDecline);

        sheet.setContentView(root);
        sheet.show();
    }

    private void showBlockUserConfirmDialog() {
        String userName = binding.tvUserProfileName.getText().toString();
        new AlertDialog.Builder(this)
                .setTitle("Chặn người dùng")
                .setMessage("Bạn có chắc muốn chặn " + userName + "? Người này sẽ không thể nhắn tin hoặc xem bài viết của bạn.")
                .setPositiveButton("Chặn", (d, w) -> {
                    String myUid = FirebaseManager.getCurrentUserId();
                    if (myUid == null || viewedUserUid == null) {
                        Toast.makeText(this, "Không thể chặn người dùng này", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    friendService.blockUser(myUid, viewedUserUid,
                        new FirebaseFriendService.ActionCallback() {
                            @Override public void onSuccess(String msg) {
                                runOnUiThread(() -> { Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show(); setupFriendButton("BLOCKED"); });
                            }
                            @Override public void onError(String err) {
                                runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "Không thể chặn người dùng", Toast.LENGTH_SHORT).show());
                            }
                        });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void showReportUserDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(64, 48, 64, 48);

        // Header
        TextView header = new TextView(this);
        header.setText("🚩 Báo cáo " + binding.tvUserProfileName.getText().toString());
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.danger_red, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("Chọn lý do báo cáo:");
        subtitle.setTextSize(14);
        subtitle.setTextColor(getResources().getColor(R.color.text_secondary, null));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = 24;
        subtitle.setLayoutParams(subParams);
        root.addView(subtitle);

        String[] reasons = {
                "Hành vi không phù hợp",
                "Nội dung phản cảm",
                "Lừa đảo / spam",
                "Quấy rối người khác",
                "Thông tin giả mạo",
                "Lý do khác"
        };

        final int[] selectedIndex = {-1};
        final TextView[] reasonViews = new TextView[reasons.length];

        for (int i = 0; i < reasons.length; i++) {
            final int index = i;
            TextView tvReason = new TextView(this);
            tvReason.setText(reasons[i]);
            tvReason.setTextSize(15);
            tvReason.setTextColor(getResources().getColor(R.color.text_primary, null));
            tvReason.setPadding(32, 28, 32, 28);
            tvReason.setBackgroundResource(android.R.drawable.list_selector_background);
            tvReason.setClickable(true);

            LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            reasonParams.topMargin = 8;
            tvReason.setLayoutParams(reasonParams);

            tvReason.setOnClickListener(v -> {
                if (selectedIndex[0] >= 0) {
                    reasonViews[selectedIndex[0]].setTextColor(getResources().getColor(R.color.text_primary, null));
                    reasonViews[selectedIndex[0]].setTypeface(null, android.graphics.Typeface.NORMAL);
                }
                selectedIndex[0] = index;
                tvReason.setTextColor(getResources().getColor(R.color.danger_red, null));
                tvReason.setTypeface(null, android.graphics.Typeface.BOLD);
            });

            reasonViews[i] = tvReason;
            root.addView(tvReason);
        }

        // Description input
        TextView descLabel = new TextView(this);
        descLabel.setText("Mô tả thêm (tùy chọn):");
        descLabel.setTextSize(14);
        descLabel.setTextColor(getResources().getColor(R.color.text_secondary, null));
        LinearLayout.LayoutParams descLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLabelParams.topMargin = 24;
        descLabel.setLayoutParams(descLabelParams);
        root.addView(descLabel);

        EditText etDescription = new EditText(this);
        etDescription.setHint("Nhập mô tả chi tiết...");
        etDescription.setTextSize(14);
        etDescription.setMinLines(2);
        etDescription.setMaxLines(4);
        etDescription.setBackground(getResources().getDrawable(R.drawable.bg_search_bar, null));
        etDescription.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.topMargin = 8;
        etDescription.setLayoutParams(etParams);
        root.addView(etDescription);

        // Submit button
        MaterialButton btnSubmit = new MaterialButton(this);
        btnSubmit.setText("Gửi báo cáo");
        btnSubmit.setAllCaps(false);
        btnSubmit.setCornerRadius(48);
        btnSubmit.setBackgroundTintList(getResources().getColorStateList(R.color.danger_red, null));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120);
        btnParams.topMargin = 32;
        btnSubmit.setLayoutParams(btnParams);
        btnSubmit.setOnClickListener(v -> {
            if (selectedIndex[0] < 0) {
                Toast.makeText(this, "Vui lòng chọn lý do báo cáo", Toast.LENGTH_SHORT).show();
                return;
            }
            sheet.dismiss();
            submitReportToFirestore(viewedUserUid, reasons[selectedIndex[0]],
                    etDescription.getText().toString().trim());
        });
        root.addView(btnSubmit);

        sheet.setContentView(root);
        sheet.show();
    }

    private void submitReportToFirestore(String targetUid, String reason, String description) {
        String myUid = FirebaseManager.getCurrentUserId();
        Map<String, Object> report = new HashMap<>();
        report.put("reporterUid", myUid);
        report.put("targetUid",   targetUid);
        report.put("reason",      reason);
        report.put("description", description);
        report.put("targetType",  "USER");
        report.put("createdAt",   com.google.firebase.firestore.FieldValue.serverTimestamp());
        FirebaseManager.getFirestore().collection("reports").add(report)
            .addOnSuccessListener(ref -> runOnUiThread(() ->
                Toast.makeText(this, "Đã gửi báo cáo. Cảm ơn bạn!", Toast.LENGTH_SHORT).show()))
            .addOnFailureListener(e -> runOnUiThread(() ->
                Toast.makeText(this, "Không thể gửi báo cáo. Thử lại sau.", Toast.LENGTH_SHORT).show()));
    }

    private void loadSavedAvatar() {
        String path = getSharedPreferences("weconnect_prefs", MODE_PRIVATE)
                .getString("user_avatar_uri", null);
        if (path != null) {
            try {
                java.io.File file = new java.io.File(path);
                if (file.exists()) {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path);
                    if (bitmap != null) {
                        binding.ivUserProfileAvatar.setImageBitmap(bitmap);
                        return;
                    }
                }
            } catch (Exception ignored) {}
            binding.ivUserProfileAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }
    }

    private void showAvatarOptionsSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        root.setPadding(0, 0, 0, 48);

        // Header
        TextView header = new TextView(this);
        header.setText("Ảnh đại diện");
        header.setTextSize(20);
        header.setTextColor(getResources().getColor(R.color.primary_pink, null));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 48, 0, 24);
        root.addView(header);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFFE8E4DE);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(div);

        // View avatar option
        LinearLayout viewRow = createOptionRow(
                "📷", "Xem ảnh đại diện", "Xem ảnh toàn màn hình",
                getResources().getColor(R.color.text_primary, null));
        viewRow.setOnClickListener(v -> {
            sheet.dismiss();
            // Show full-screen avatar dialog
            Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            ImageView imageView = new ImageView(this);
            imageView.setImageDrawable(binding.ivUserProfileAvatar.getDrawable());
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(Color.BLACK);
            imageView.setOnClickListener(v2 -> dialog.dismiss());
            dialog.setContentView(imageView);
            dialog.show();
        });
        root.addView(viewRow);

        // Divider
        View div2 = new View(this);
        div2.setBackgroundColor(0xFFE8E4DE);
        div2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(div2);

        // Choose from gallery option
        LinearLayout galleryRow = createOptionRow(
                "🖼", "Chọn ảnh đại diện từ thư viện", "Chọn ảnh mới từ bộ sưu tập",
                getResources().getColor(R.color.text_primary, null));
        galleryRow.setOnClickListener(v -> {
            sheet.dismiss();
            Intent pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
            pickIntent.setType("image/*");
            pickIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pickIntent, 1001);
        });
        root.addView(galleryRow);

        sheet.setContentView(root);
        sheet.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            android.net.Uri selectedImage = data.getData();
            if (selectedImage != null) {
                // Copy ảnh vào internal storage để luôn truy cập được
                try {
                    java.io.InputStream is = getContentResolver().openInputStream(selectedImage);
                    if (is != null) {
                        java.io.File avatarFile = new java.io.File(getFilesDir(), "avatar.jpg");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(avatarFile);
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                        is.close();

                        // Load từ file đã copy
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                        binding.ivUserProfileAvatar.setImageBitmap(bitmap);

                        // Lưu path vào SharedPreferences
                        getSharedPreferences("weconnect_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("user_avatar_uri", avatarFile.getAbsolutePath())
                                .apply();

                        Toast.makeText(this, "Đã cập nhật ảnh đại diện!", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Lỗi khi lưu ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == 2001 && resultCode == RESULT_OK && data != null) {
            long editPostId = data.getLongExtra("edit_post_id", -1);
            if (editPostId != -1) {
                updatePostViaApi(editPostId, data);
            }
        }
    }

    private void updatePostViaApi(long postId, Intent data) {
        String imageUri = data.getStringExtra("post_image_uri");
        if (imageUri != null && imageUri.startsWith("content://")) {
            try {
                android.net.Uri uri = android.net.Uri.parse(imageUri);
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) mimeType = "image/jpeg";
                String ext = mimeType.contains("png") ? ".png" : mimeType.contains("webp") ? ".webp" : ".jpg";
                String fileName = "post_images/" + System.currentTimeMillis() + ext;

                com.google.firebase.storage.FirebaseStorage storage = com.google.firebase.storage.FirebaseStorage.getInstance();
                com.google.firebase.storage.StorageReference ref = storage.getReference().child(fileName);
                ref.putFile(uri).continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) throw task.getException();
                    return ref.getDownloadUrl();
                }).addOnCompleteListener(task -> {
                    String downloadUrl = task.isSuccessful() && task.getResult() != null ? task.getResult().toString() : null;
                    sendUpdatePostFirestore(postId, data, downloadUrl);
                });
                return;
            } catch (Exception e) {
                android.util.Log.e("UPLOAD_IMAGE", "Error: " + e.getMessage());
            }
        }
        sendUpdatePostFirestore(postId, data, imageUri);
    }

    private void sendUpdatePostFirestore(long postId, Intent data, String imageUrl) {
        String postDocId = String.valueOf(postId);
        java.util.Map<String, Object> update = new java.util.HashMap<>();
        update.put("content",    data.getStringExtra("post_content"));
        update.put("interestTag",data.getStringExtra("post_tag"));
        update.put("location",   data.getStringExtra("post_location"));
        update.put("maxMembers", data.getIntExtra("post_max_members", 10));
        if (imageUrl != null) update.put("imageUrl", imageUrl);
        update.put("updatedAt",  com.google.firebase.firestore.FieldValue.serverTimestamp());

        FirebaseManager.getFirestore().collection("posts").document(postDocId)
            .update(update)
            .addOnSuccessListener(v -> runOnUiThread(() -> {
                Toast.makeText(this, "Đã cập nhật bài viết!", Toast.LENGTH_SHORT).show();
                bindActivePosts();
            }))
            .addOnFailureListener(e -> runOnUiThread(() ->
                Toast.makeText(this, "Không thể cập nhật bài viết", Toast.LENGTH_SHORT).show()));
    }
}
