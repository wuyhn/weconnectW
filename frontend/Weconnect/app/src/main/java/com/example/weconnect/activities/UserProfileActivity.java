package com.example.weconnect.activities;

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
import com.example.weconnect.adapters.PostAdapter;
import com.example.weconnect.adapters.UserReviewAdapter;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.ReviewApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.util.BadgeManager;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.data.FakeSocialRepository;
import com.example.weconnect.websocket.WebSocketManager;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.UserProfile;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.PostResponse;
import com.example.weconnect.models.UserReview;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserProfileActivity extends AppCompatActivity {

    private ImageView ivBackUserProfile;
    private ImageView ivMenuProfile;
    private ImageView ivUserProfileAvatar;
    private TextView tvUserProfileName;
    private TextView tvUserReputation;
    private TextView tvUserBio;
    private TextView tvUserBirthday;
    private TextView tvUserGender;
    private MaterialButton btnAddFriend;
    private MaterialButton btnMessage;
    private MaterialButton btnViewArchive;
    private MaterialButton btnRateUser;
    private MaterialButton btnReportUser;
    private LinearLayout layoutSocialButtons;
    private LinearLayout layoutRateReport;
    private TextView tvFriendCount;
    private RecyclerView rvUserReviews;
    private ChipGroup chipGroupUserInterests;
    private View footerNavigationProfile;

    private DrawerLayout drawerLayoutProfile;
    private LinearLayout menuEditProfile;
    private LinearLayout menuChangePassword;
    private LinearLayout menuDeleteAccount;

    private RecyclerView rvActivePostsProfile;
    private View tvNoActivePosts;
    private TextView tvInterestsTitle;

    private View cardCreatePostProfile;
    private TextView tvCreatePostHint;
    private TextView tvReviewsTitle;
    private android.widget.TextView tvNotifBadge;

    // Related posts (from other users matching interest tags)
    private TextView tvRelatedPostsTitle;
    private TextView tvNoRelatedPosts;
    private RecyclerView rvRelatedPosts;

    private String username;
    private long viewedUserId = -1; // ID của user đang xem (dùng cho friend API)
    private FakeSocialRepository socialRepository;
    private PostApiService postApiService;
    private ReviewApiService reviewApiService;
    private com.example.weconnect.api.FriendApiService friendApiService;
    private ActivityResultLauncher<Intent> createPostLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        socialRepository = FakeSocialRepository.getInstance();
        RetrofitClient.loadToken(this);
        postApiService = RetrofitClient.getClient().create(PostApiService.class);
        reviewApiService = RetrofitClient.getClient().create(ReviewApiService.class);
        friendApiService = RetrofitClient.getClient().create(com.example.weconnect.api.FriendApiService.class);
        setupCreatePostLauncher();
        initViews();
        bindFakeUserProfile();
        setupClickListeners();
        bindSocialState();
        setupDrawerMenu();
        setupProfileTabs();
        bindActivePosts();
        loadMyActivities();
        // Ẩn phần gợi ý bài viết (chỉ giữ gợi ý user)
        hideRelatedPosts();
    }

    private void setupCreatePostLauncher() {
        createPostLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String content = data.getStringExtra("post_content");
                        String tag = data.getStringExtra("post_tag");
                        String location = data.getStringExtra("post_location");
                        int maxMembers = data.getIntExtra("post_max_members", 10);
                        String imageUri = data.getStringExtra("post_image_uri");
                        long endTimeMillis = data.getLongExtra("post_end_time",
                                System.currentTimeMillis() + 24L * 60L * 60L * 1000L);
                        createPostViaApi(content, tag, location, maxMembers, imageUri, endTimeMillis);
                    }
                }
        );
    }

    private void createPostViaApi(String content, String tag, String location,
                                  int maxMembers, String imageUri, long endTimeMillis) {
        if (imageUri != null) {
            // Upload image first, then create post with server URL
            uploadImageThenCreatePost(content, tag, location, maxMembers, imageUri, endTimeMillis);
        } else {
            sendCreatePostProfile(content, tag, location, maxMembers, null, endTimeMillis);
        }
    }

    private void uploadImageThenCreatePost(String content, String tag, String location,
                                           int maxMembers, String imageUri, long endTimeMillis) {
        try {
            android.net.Uri uri = android.net.Uri.parse(imageUri);
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                sendCreatePostProfile(content, tag, location, maxMembers, imageUri, endTimeMillis);
                return;
            }
            byte[] bytes = readAllBytesProfile(inputStream);
            inputStream.close();

            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "image/jpeg";
            String ext = mimeType.contains("png") ? ".png" : mimeType.contains("webp") ? ".webp" : ".jpg";
            String fileName = "post_image_" + System.currentTimeMillis() + ext;

            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse(mimeType), bytes);
            okhttp3.MultipartBody.Part filePart =
                    okhttp3.MultipartBody.Part.createFormData("file", fileName, requestBody);

            postApiService.uploadImage(filePart).enqueue(new Callback<ApiResponse<String>>() {
                @Override
                public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                    String serverUrl = imageUri;
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        serverUrl = response.body().getResult();
                    }
                    sendCreatePostProfile(content, tag, location, maxMembers, serverUrl, endTimeMillis);
                }
                @Override
                public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                    sendCreatePostProfile(content, tag, location, maxMembers, imageUri, endTimeMillis);
                }
            });
        } catch (Exception e) {
            sendCreatePostProfile(content, tag, location, maxMembers, imageUri, endTimeMillis);
        }
    }

    private byte[] readAllBytesProfile(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private void sendCreatePostProfile(String content, String tag, String location,
                                       int maxMembers, String imageUrl, long endTimeMillis) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("interestTag", tag);
        body.put("location", location);
        body.put("maxMembers", maxMembers);
        if (imageUrl != null) {
            body.put("imageUrl", imageUrl);
        }
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        body.put("startTime", isoFormat.format(new Date()));
        body.put("endTime", isoFormat.format(new Date(endTimeMillis)));

        postApiService.createPost(body).enqueue(new Callback<ApiResponse<PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PostResponse>> call,
                                   Response<ApiResponse<PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(UserProfileActivity.this, "Đã tạo bài đăng!", Toast.LENGTH_SHORT).show();
                    bindActivePosts();
                } else {
                    String errorMsg = "Không thể tạo bài đăng";
                    if (response.body() != null && response.body().getMessage() != null) {
                        errorMsg = response.body().getMessage();
                    }
                    Toast.makeText(UserProfileActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PostResponse>> call, Throwable t) {
                Toast.makeText(UserProfileActivity.this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        BadgeManager.applyBadge(tvNotifBadge);
        // Refresh state khi quay lại (vd: sau khi chấp nhận kết bạn từ thông báo)
        bindSocialState();
        // Refresh bài viết khi quay lại (vd: sau khi tạo bài mới)
        bindActivePosts();
        loadMyActivities();
        // Ẩn phần gợi ý bài viết (chỉ giữ gợi ý user)
        hideRelatedPosts();
        // Refresh profile data từ API (sau khi chỉnh sửa profile)
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        if (!viewOther) {
            loadOwnProfileName();
        }
    }

    private void initViews() {
        ivBackUserProfile = findViewById(R.id.ivBackUserProfile);
        ivMenuProfile = findViewById(R.id.ivMenuProfile);
        ivUserProfileAvatar = findViewById(R.id.ivUserProfileAvatar);
        tvUserProfileName = findViewById(R.id.tvUserProfileName);
        tvUserReputation = findViewById(R.id.tvUserReputation);
        tvUserBio = findViewById(R.id.tvUserBio);
        tvUserBirthday = findViewById(R.id.tvUserBirthday);
        tvUserGender = findViewById(R.id.tvUserGender);
        btnAddFriend = findViewById(R.id.btnAddFriend);
        btnMessage = findViewById(R.id.btnMessage);
        btnViewArchive = findViewById(R.id.btnViewArchive);
        btnRateUser = findViewById(R.id.btnRateUser);
        btnReportUser = findViewById(R.id.btnReportUser);
        layoutSocialButtons = findViewById(R.id.layoutSocialButtons);
        layoutRateReport = findViewById(R.id.layoutRateReport);
        tvFriendCount = findViewById(R.id.tvFriendCount);
        rvUserReviews = findViewById(R.id.rvUserReviews);
        chipGroupUserInterests = findViewById(R.id.chipGroupUserInterests);
        footerNavigationProfile = findViewById(R.id.footerNavigationProfile);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);

        drawerLayoutProfile = findViewById(R.id.drawerLayoutProfile);
        menuEditProfile = findViewById(R.id.menuEditProfile);
        menuChangePassword = findViewById(R.id.menuChangePassword);
        menuDeleteAccount = findViewById(R.id.menuDeleteAccount);

        rvActivePostsProfile = findViewById(R.id.rvActivePostsProfile);
        tvNoActivePosts = findViewById(R.id.tvNoActivePosts);
        tvInterestsTitle = findViewById(R.id.tvInterestsTitle);

        cardCreatePostProfile = findViewById(R.id.cardCreatePostProfile);
        tvCreatePostHint = findViewById(R.id.tvCreatePostHint);
        tvReviewsTitle = findViewById(R.id.tvReviewsTitle);
        tvRelatedPostsTitle = findViewById(R.id.tvRelatedPostsTitle);
        tvNoRelatedPosts = findViewById(R.id.tvNoRelatedPosts);
        rvRelatedPosts = findViewById(R.id.rvRelatedPosts);
    }

    private void setupProfileTabs() {
        com.google.android.material.tabs.TabLayout tabLayout = findViewById(R.id.tabLayoutProfile);
        LinearLayout containerMyPosts = findViewById(R.id.containerMyPosts);
        LinearLayout containerMyActivities = findViewById(R.id.containerMyActivities);

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
        ivBackUserProfile.setOnClickListener(v -> finish());

        btnViewArchive.setOnClickListener(v -> {
            Intent intent = new Intent(this, ArchivePostsActivity.class);
            intent.putExtra("username", tvUserProfileName.getText().toString());
            long archiveUserId = getIntent().getLongExtra("user_id", -1);
            if (archiveUserId <= 0) {
                android.content.SharedPreferences prefs = getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
                archiveUserId = prefs.getLong("user_id", -1);
            }
            intent.putExtra("user_id", archiveUserId);
            startActivity(intent);
        });

        // Bottom navigation click listeners
        View btnHome = findViewById(R.id.btnHomeProfile);
        View btnMessages = findViewById(R.id.btnMessagesProfile);
        View btnNotifications = findViewById(R.id.btnNotificationsProfile);

        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
        if (btnMessages != null) {
            btnMessages.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChatListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v -> {
                Intent intent = new Intent(this, NotificationsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }

    private void setupDrawerMenu() {
        drawerLayoutProfile.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        ivMenuProfile.setOnClickListener(v ->
                drawerLayoutProfile.openDrawer(Gravity.END)
        );

        menuEditProfile.setOnClickListener(v -> {
            drawerLayoutProfile.closeDrawer(Gravity.END);
            startActivityForResult(new Intent(this, EditProfileActivity.class), 3001);
        });

        menuChangePassword.setOnClickListener(v -> {
            drawerLayoutProfile.closeDrawer(Gravity.END);
            startActivity(new Intent(this, ChangePasswordActivity.class));
        });

        menuDeleteAccount.setOnClickListener(v -> {
            drawerLayoutProfile.closeDrawer(Gravity.END);
            showDeleteAccountDialog();
        });

        LinearLayout menuLogout = findViewById(R.id.menuLogout);
        menuLogout.setOnClickListener(v -> {
            drawerLayoutProfile.closeDrawer(Gravity.END);
            new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc muốn đăng xuất?")
                    .setPositiveButton("Đăng xuất", (d, w) -> {
                        // Clear session & reset all fake repos
                        WebSocketManager.getInstance().disconnect();
                        RetrofitClient.clearSession(this);
                        FakeSocialRepository.resetInstance();
                        com.example.weconnect.data.FakePostRepository.resetInstance();
                        com.example.weconnect.data.FakeNotificationRepository.resetInstance();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Huỷ", null)
                    .show();
        });

        LinearLayout menuBlockedUsers = findViewById(R.id.menuBlockedUsers);
        menuBlockedUsers.setOnClickListener(v -> {
            drawerLayoutProfile.closeDrawer(Gravity.END);
            startActivity(new Intent(this, BlockedUsersActivity.class));
        });

        LinearLayout menuLegal = findViewById(R.id.menuLegal);
        menuLegal.setOnClickListener(v -> {
            drawerLayoutProfile.closeDrawer(Gravity.END);
            startActivity(new Intent(this, LegalActivity.class));
        });
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Xoá tài khoản")
                .setMessage("Bạn có chắc chắn muốn xoá tài khoản? Hành động này không thể hoàn tác.")
                .setPositiveButton("Xoá", (dialog, which) -> {
                    RetrofitClient.loadToken(this);
                    com.example.weconnect.api.UserApiService userApi =
                            RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);
                    userApi.deleteAccount().enqueue(new Callback<ApiResponse<Void>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Void>> call,
                                               Response<ApiResponse<Void>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(UserProfileActivity.this,
                                        "Đã xoá tài khoản thành công!", Toast.LENGTH_SHORT).show();
                                // Clear session & reset all fake repos
                                WebSocketManager.getInstance().disconnect();
                                RetrofitClient.clearSession(UserProfileActivity.this);
                                FakeSocialRepository.resetInstance();
                                com.example.weconnect.data.FakePostRepository.resetInstance();
                                com.example.weconnect.data.FakeNotificationRepository.resetInstance();
                                // Navigate to login
                                Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(UserProfileActivity.this,
                                        "Không thể xoá tài khoản. Thử lại sau.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                            Toast.makeText(UserProfileActivity.this,
                                    "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void bindFakeUserProfile() {
        username = getIntent().getStringExtra("username");
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        long targetUserId = getIntent().getLongExtra("user_id", -1);
        viewedUserId = targetUserId;
        
        if (username == null || username.isEmpty()) {
            if (targetUserId > 0) {
                username = "Người dùng #" + targetUserId;
            } else {
                // Load tên thật từ SharedPreferences (đã lưu khi đăng nhập/đăng ký)
                String savedName = RetrofitClient.getUserName(this);
                username = (savedName != null && !savedName.isEmpty())
                        ? savedName : socialRepository.getCurrentUsername();
            }
        }

        ivUserProfileAvatar.setImageResource(R.drawable.ic_user_placeholder);
        tvUserProfileName.setText(username);
        tvUserReputation.setText("0");
        
        rvUserReviews.setLayoutManager(new LinearLayoutManager(this));
        // Load reviews from backend
        loadReviewsFromBackend();

        // Load interests from backend
        loadInterestsFromBackend();

        // Load tên thật từ backend
        if (viewOther && viewedUserId > 0) {
            // Xem profile người khác: load tên thật từ API
            loadOtherUserProfile(viewedUserId);
        } else {
            loadOwnProfileName();
        }
    }

    private void loadOtherUserProfile(long userId) {
        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);

        userApi.getUserProfile(userId).enqueue(new Callback<ApiResponse<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<java.util.Map<String, Object>>> call,
                                   Response<ApiResponse<java.util.Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    java.util.Map<String, Object> profile = response.body().getResult();
                    String fullName = profile.get("fullName") != null
                            ? profile.get("fullName").toString() : null;
                    if (fullName != null && !fullName.isEmpty()) {
                        username = fullName;
                        tvUserProfileName.setText(fullName);
                    }
                    // Load avatar with Glide
                    String avatarUrl = profile.get("avatarUrl") != null
                            ? profile.get("avatarUrl").toString() : null;
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        if (avatarUrl.startsWith("/")) {
                            avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                        }
                        com.bumptech.glide.Glide.with(UserProfileActivity.this)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_user_placeholder)
                                .error(R.drawable.ic_user_placeholder)
                                .circleCrop()
                                .into(ivUserProfileAvatar);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                // Giữ tên từ intent
            }
        });
    }

    private void loadOwnProfileName() {
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        if (viewOther) return;

        long targetUserId = getIntent().getLongExtra("user_id", -1);
        if (targetUserId > 0) return; // Đang xem profile người khác

        RetrofitClient.loadToken(this);
        long myId = RetrofitClient.getUserId(this);
        if (myId <= 0) return;

        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);

        userApi.getUserProfile(myId).enqueue(new Callback<ApiResponse<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<java.util.Map<String, Object>>> call,
                                   Response<ApiResponse<java.util.Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    java.util.Map<String, Object> profile = response.body().getResult();

                    // Tên
                    String fullName = profile.get("fullName") != null
                            ? profile.get("fullName").toString() : null;
                    if (fullName != null && !fullName.isEmpty()) {
                        username = fullName;
                        tvUserProfileName.setText(fullName);
                        RetrofitClient.saveUserName(UserProfileActivity.this, fullName);
                        socialRepository.setCurrentUsername(fullName);
                    }

                    // Bio
                    String bio = profile.get("bio") != null
                            ? profile.get("bio").toString() : "";
                    if (tvUserBio != null) {
                        tvUserBio.setText(bio.isEmpty() ? "" : bio);
                        tvUserBio.setVisibility(bio.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    // Gender
                    String gender = profile.get("gender") != null
                            ? profile.get("gender").toString() : "";
                    if (tvUserGender != null) {
                        tvUserGender.setText(gender.isEmpty() ? "" : "Giới tính: " + gender);
                        tvUserGender.setVisibility(gender.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    // Birthday
                    String birthday = profile.get("birthday") != null
                            ? profile.get("birthday").toString() : "";
                    if (tvUserBirthday != null) {
                        tvUserBirthday.setText(birthday.isEmpty() ? "" : "🎂 " + birthday);
                        tvUserBirthday.setVisibility(birthday.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    // Reputation (từ API thay vì hardcode)
                    Object repObj = profile.get("reputationScore");
                    if (repObj != null && tvUserReputation != null) {
                        int rep = ((Number) repObj).intValue();
                        tvUserReputation.setText(String.valueOf(rep));
                    }

                    // Load avatar with Glide + persist URL globally
                    String avatarUrl = profile.get("avatarUrl") != null
                            ? profile.get("avatarUrl").toString() : null;
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        if (avatarUrl.startsWith("/")) {
                            avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                        }
                        RetrofitClient.saveAvatarUrl(UserProfileActivity.this, avatarUrl);
                        com.bumptech.glide.Glide.with(UserProfileActivity.this)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_user_placeholder)
                                .error(R.drawable.ic_user_placeholder)
                                .circleCrop()
                                .into(ivUserProfileAvatar);
                    }

                    // Interests
                    String interestTags = profile.get("interestTags") != null
                            ? profile.get("interestTags").toString() : "";
                    if (!interestTags.isEmpty() && chipGroupUserInterests != null) {
                        chipGroupUserInterests.removeAllViews();
                        String[] tags = interestTags.split(",");
                        for (String tag : tags) {
                            String trimmed = tag.trim();
                            if (!trimmed.isEmpty()) {
                                com.google.android.material.chip.Chip chip =
                                        new com.google.android.material.chip.Chip(UserProfileActivity.this);
                                chip.setText(trimmed);
                                chip.setClickable(false);
                                chip.setCheckable(false);
                                chipGroupUserInterests.addView(chip);
                            }
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                // Giữ tên từ SharedPreferences
            }
        });
    }

    private void loadInterestsFromBackend() {
        com.example.weconnect.api.RetrofitClient.loadToken(this);
        String token = com.example.weconnect.api.RetrofitClient.getAuthToken();

        if (token == null) {
            // Fallback: load from SharedPreferences
            loadInterestsFromLocal();
            return;
        }

        com.example.weconnect.api.UserApiService apiService = 
                com.example.weconnect.api.RetrofitClient.getClient()
                        .create(com.example.weconnect.api.UserApiService.class);

        // Determine if viewing own profile or someone else's
        boolean isOwnProfile = socialRepository.getCurrentUsername().equalsIgnoreCase(username);

        if (isOwnProfile) {
            apiService.getInterests().enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<java.util.List<String>>>() {
                @Override
                public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<String>>> call,
                                       retrofit2.Response<com.example.weconnect.models.ApiResponse<java.util.List<String>>> response) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getResult() != null) {
                        displayInterests(response.body().getResult());
                    } else {
                        loadInterestsFromLocal();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<String>>> call, Throwable t) {
                    loadInterestsFromLocal();
                }
            });
        } else {
            // For other users, try to load from their profile
            long targetUserId = getIntent().getLongExtra("user_id", -1);
            if (targetUserId > 0) {
                apiService.getUserProfile(targetUserId).enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.Map<String, Object>>> call,
                                           retrofit2.Response<com.example.weconnect.models.ApiResponse<java.util.Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            java.util.Map<String, Object> profile = response.body().getResult();
                            String interestTags = profile.get("interestTags") != null
                                    ? profile.get("interestTags").toString() : "";
                            if (!interestTags.isEmpty()) {
                                displayInterests(java.util.Arrays.asList(interestTags.split(",")));
                            } else {
                                displayInterests(new ArrayList<>());
                            }
                            // Update profile info
                            String fullName = profile.get("fullName") != null
                                    ? profile.get("fullName").toString() : username;
                            tvUserProfileName.setText(fullName);
                        } else {
                            loadInterestsFromLocal();
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                        loadInterestsFromLocal();
                    }
                });
            } else {
                loadInterestsFromLocal();
            }
        }
    }

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
        chipGroupUserInterests.removeAllViews();
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
            chipGroupUserInterests.addView(chip);
        }
    }

    private void bindActivePosts() {
        long targetUserId = getIntent().getLongExtra("user_id", -1);
        if (targetUserId <= 0) {
            // Own profile - load from shared prefs
            android.content.SharedPreferences prefs = getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
            targetUserId = prefs.getLong("user_id", -1);
        }

        if (targetUserId <= 0) {
            // Fallback to fake data
            showActivePosts(FakePostRepository.getInstance().getActivePostsForUser(username));
            return;
        }

        final long userId = targetUserId;
        // Backend getUserActivePosts đã lọc sẵn: archived=false AND endTime > now
        // Không cần lọc lại ở frontend
        postApiService.getUserPosts(userId).enqueue(new Callback<ApiResponse<List<PostResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PostResponse>>> call,
                                   Response<ApiResponse<List<PostResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<PostResponse> allResponses = response.body().getResult();
                    List<Post> userPosts = new ArrayList<>();
                    if (allResponses != null) {
                        for (PostResponse pr : allResponses) {
                            userPosts.add(pr.toPost());
                        }
                    }
                    showActivePosts(userPosts);
                } else {
                    showActivePosts(FakePostRepository.getInstance().getActivePostsForUser(username));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PostResponse>>> call, Throwable t) {
                showActivePosts(FakePostRepository.getInstance().getActivePostsForUser(username));
            }
        });
    }

    private void showActivePosts(List<Post> activePosts) {
        if (activePosts.isEmpty()) {
            tvNoActivePosts.setVisibility(View.VISIBLE);
            rvActivePostsProfile.setVisibility(View.GONE);
        } else {
            tvNoActivePosts.setVisibility(View.GONE);
            rvActivePostsProfile.setVisibility(View.VISIBLE);
            rvActivePostsProfile.setLayoutManager(new LinearLayoutManager(this));

            // Nếu xem profile người khác → truyền viewer interests để kiểm soát nút tham gia
            boolean viewOther = getIntent().getBooleanExtra("view_other", false);
            if (viewOther) {
                java.util.Set<String> myInterests = getMyInterestSet();
                rvActivePostsProfile.setAdapter(new PostAdapter(this, activePosts, myInterests));
            } else {
                rvActivePostsProfile.setAdapter(new PostAdapter(this, activePosts));
            }
        }
    }

    /**
     * Load hoạt động đã tham gia: cho cả profile mình và profile người khác
     */
    private void loadMyActivities() {
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        long targetUserId = getIntent().getLongExtra("user_id", -1);

        RetrofitClient.loadToken(this);

        if (viewOther && targetUserId > 0) {
            // Xem profile người khác → load activities của họ (CTA resolve theo viewer)
            postApiService.getUserActivities(targetUserId).enqueue(new Callback<ApiResponse<List<PostResponse>>>() {
                @Override
                public void onResponse(Call<ApiResponse<List<PostResponse>>> call,
                                       Response<ApiResponse<List<PostResponse>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        List<PostResponse> responses = response.body().getResult();
                        List<Post> activities = new ArrayList<>();
                        if (responses != null) {
                            for (PostResponse pr : responses) {
                                activities.add(pr.toPost());
                            }
                        }
                        showMyActivities(activities);
                    } else {
                        showMyActivities(new ArrayList<>());
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<List<PostResponse>>> call, Throwable t) {
                    showMyActivities(new ArrayList<>());
                }
            });
        } else {
            // Profile của mình
            postApiService.getMyActivities().enqueue(new Callback<ApiResponse<List<PostResponse>>>() {
                @Override
                public void onResponse(Call<ApiResponse<List<PostResponse>>> call,
                                       Response<ApiResponse<List<PostResponse>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        List<PostResponse> responses = response.body().getResult();
                        List<Post> myActivities = new ArrayList<>();
                        if (responses != null) {
                            for (PostResponse pr : responses) {
                                myActivities.add(pr.toPost());
                            }
                        }
                        showMyActivities(myActivities);
                    } else {
                        showMyActivities(new ArrayList<>());
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<List<PostResponse>>> call, Throwable t) {
                    showMyActivities(new ArrayList<>());
                }
            });
        }
    }

    private void showMyActivities(List<Post> activities) {
        View tvEmpty = findViewById(R.id.tvNoMyActivities);
        RecyclerView rv = findViewById(R.id.rvMyActivities);

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

    /**
     * Load bài viết gợi ý từ người dùng khác dựa trên sở thích chung.
     * Gọi API getActivePosts rồi lọc: cùng tag sở thích, khác tác giả.
     */
    private void loadRelatedPosts() {
        // Lấy sở thích của user hiện tại
        android.content.SharedPreferences prefs = getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
        String savedInterests = prefs.getString("user_interests", "");

        if (savedInterests.isEmpty()) {
            // Thử load từ API trước
            RetrofitClient.loadToken(this);
            com.example.weconnect.api.UserApiService userApi =
                    RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);
            userApi.getInterests().enqueue(new retrofit2.Callback<ApiResponse<java.util.List<String>>>() {
                @Override
                public void onResponse(retrofit2.Call<ApiResponse<java.util.List<String>>> call,
                                       retrofit2.Response<ApiResponse<java.util.List<String>>> response) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getResult() != null
                            && !response.body().getResult().isEmpty()) {
                        java.util.List<String> interests = response.body().getResult();
                        prefs.edit().putString("user_interests", String.join(",", interests)).apply();
                        fetchAndFilterRelatedPosts(interests);
                    } else {
                        hideRelatedPosts();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<ApiResponse<java.util.List<String>>> call, Throwable t) {
                    hideRelatedPosts();
                }
            });
            return;
        }

        java.util.List<String> interests = java.util.Arrays.asList(savedInterests.split(","));
        fetchAndFilterRelatedPosts(interests);
    }

    private void fetchAndFilterRelatedPosts(java.util.List<String> userInterests) {
        // Tạo set sở thích để so sánh nhanh
        java.util.Set<String> interestSet = new java.util.HashSet<>();
        for (String tag : userInterests) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) interestSet.add(trimmed.toLowerCase());
        }

        if (interestSet.isEmpty()) {
            hideRelatedPosts();
            return;
        }

        // Lấy tên user hiện tại để loại bỏ bài của chính mình
        String currentUserName = RetrofitClient.getUserName(this);

        // Dùng username của profile đang xem để loại bỏ bài của chính họ
        postApiService.getActivePosts().enqueue(new Callback<ApiResponse<java.util.List<PostResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<java.util.List<PostResponse>>> call,
                                   Response<ApiResponse<java.util.List<PostResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    java.util.List<PostResponse> allPosts = response.body().getResult();
                    java.util.List<Post> related = new ArrayList<>();
                    if (allPosts != null) {
                        for (PostResponse pr : allPosts) {
                            // Bỏ qua bài của chính user đang xem profile
                            if (pr.getAuthorName() != null
                                    && pr.getAuthorName().equalsIgnoreCase(username)) {
                                continue;
                            }
                            // Bỏ qua bài của chính mình (người đang đăng nhập)
                            if (currentUserName != null && pr.getAuthorName() != null
                                    && pr.getAuthorName().equalsIgnoreCase(currentUserName)) {
                                continue;
                            }
                            // Lọc bài có tag trùng sở thích
                            if (pr.getInterestTag() != null
                                    && interestSet.contains(pr.getInterestTag().trim().toLowerCase())) {
                                related.add(pr.toPost());
                            }
                        }
                    }
                    showRelatedPosts(related);
                } else {
                    hideRelatedPosts();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<java.util.List<PostResponse>>> call, Throwable t) {
                hideRelatedPosts();
            }
        });
    }

    private void showRelatedPosts(java.util.List<Post> relatedPosts) {
        if (relatedPosts.isEmpty()) {
            hideRelatedPosts();
            return;
        }
        tvRelatedPostsTitle.setVisibility(View.VISIBLE);
        tvNoRelatedPosts.setVisibility(View.GONE);
        rvRelatedPosts.setVisibility(View.VISIBLE);
        rvRelatedPosts.setLayoutManager(new LinearLayoutManager(this));
        rvRelatedPosts.setAdapter(new PostAdapter(this, relatedPosts));
    }

    private void hideRelatedPosts() {
        tvRelatedPostsTitle.setVisibility(View.GONE);
        tvNoRelatedPosts.setVisibility(View.GONE);
        rvRelatedPosts.setVisibility(View.GONE);
    }

    /**
     * Load gợi ý user có sở thích chung.
     * Hiển thị danh sách ngang: avatar + tên + nút "Thêm bạn bè".
     * Chỉ hiện khi xem profile người khác.
     */
    private void loadSuggestedUsers() {
        long targetUserId = getIntent().getLongExtra("user_id", -1);
        if (targetUserId <= 0) return;

        RetrofitClient.loadToken(this);
        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);

        userApi.getSuggestions(targetUserId).enqueue(new Callback<ApiResponse<java.util.List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<java.util.List<Map<String, Object>>>> call,
                                   Response<ApiResponse<java.util.List<Map<String, Object>>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null
                        && !response.body().getResult().isEmpty()) {
                    showSuggestedUsersUI(response.body().getResult());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<java.util.List<Map<String, Object>>>> call, Throwable t) {
                // Không hiện nếu lỗi
            }
        });
    }

    private void showSuggestedUsersUI(java.util.List<Map<String, Object>> suggestions) {
        try {
            // Tìm vị trí chèn (sau phần related posts, trước reviews)
            LinearLayout mainContent = (LinearLayout) tvRelatedPostsTitle.getParent();
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
                    socialRepository.sendFriendRequest(friendName);
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
            int insertIndex = mainContent.indexOfChild(rvRelatedPosts);
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
        long myUserId = RetrofitClient.getUserId(this);
        boolean isOwnProfile = !viewOther && (viewedUserId == -1 || viewedUserId == myUserId);

        if (isOwnProfile) {
            // === Hồ sơ của mình ===
            ivBackUserProfile.setVisibility(View.GONE);
            ivMenuProfile.setVisibility(View.VISIBLE);
            tvFriendCount.setVisibility(View.VISIBLE);
            layoutSocialButtons.setVisibility(View.GONE);
            layoutRateReport.setVisibility(View.GONE);
            btnViewArchive.setVisibility(View.VISIBLE);
            footerNavigationProfile.setVisibility(View.VISIBLE);

            ivUserProfileAvatar.setOnClickListener(v -> showAvatarOptionsSheet());

            // Load ảnh đại diện đã lưu từ SharedPreferences
            loadSavedAvatar();
            cardCreatePostProfile.setVisibility(View.VISIBLE);
            cardCreatePostProfile.setOnClickListener(v -> {
                Intent intent = new Intent(this, CreatePostActivity.class);
                createPostLauncher.launch(intent);
            });

            tvInterestsTitle.setVisibility(View.VISIBLE);
            chipGroupUserInterests.setVisibility(View.VISIBLE);
            rvActivePostsProfile.setVisibility(View.VISIBLE);

            // Load số bạn bè từ backend
            loadFriendCountFromApi();
            return;
        }

        // === Hồ sơ người khác ===
        ivBackUserProfile.setVisibility(View.VISIBLE);
        ivMenuProfile.setVisibility(View.VISIBLE); // 3-dots menu ở góc phải header
        tvFriendCount.setVisibility(View.GONE);
        btnViewArchive.setVisibility(View.GONE);
        layoutSocialButtons.setVisibility(View.VISIBLE);
        layoutRateReport.setVisibility(View.VISIBLE);
        btnReportUser.setVisibility(View.GONE); // Ẩn nút Report cũ, dùng menu 3 chấm thay
        footerNavigationProfile.setVisibility(View.GONE);
        cardCreatePostProfile.setVisibility(View.GONE);

        tvInterestsTitle.setVisibility(View.VISIBLE);
        chipGroupUserInterests.setVisibility(View.VISIBLE);
        rvActivePostsProfile.setVisibility(View.VISIBLE);
        tvReviewsTitle.setVisibility(View.VISIBLE);
        rvUserReviews.setVisibility(View.VISIBLE);

        // loadSuggestedUsers(); // Tạm ẩn phần "Gợi ý cho bạn"
        btnRateUser.setOnClickListener(v -> showRateUserDialog());

        // 3-dots menu ở header (ivMenuProfile) cho profile người khác
        ivMenuProfile.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(this, ivMenuProfile);
            popup.getMenu().add(0, 1, 0, "🚫 Chặn người dùng");
            popup.getMenu().add(0, 2, 1, "⚠️ Báo cáo");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    showBlockUserConfirmDialog();
                    return true;
                } else if (item.getItemId() == 2) {
                    showReportUserDialog();
                    return true;
                }
                return false;
            });
            popup.show();
        });

        ivUserProfileAvatar.setOnClickListener(null);
        ivUserProfileAvatar.setClickable(false);

        // Load trạng thái bạn bè từ backend
        if (viewedUserId > 0) {
            loadFriendStatusFromApi(viewedUserId);
        } else if (username != null && !username.isEmpty()) {
            // Fallback: resolve user_id từ username qua API
            lookupUserIdByName(username);
        } else {
            setupFriendButton("NONE");
        }
    }

    private void loadFriendCountFromApi() {
        friendApiService.getFriendCount().enqueue(new Callback<ApiResponse<Integer>>() {
            @Override
            public void onResponse(Call<ApiResponse<Integer>> call, Response<ApiResponse<Integer>> response) {
                int count = 0;
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    count = response.body().getResult();
                }
                int finalCount = count;
                tvFriendCount.setText("👥 Bạn bè: " + finalCount);
                tvFriendCount.setOnClickListener(v -> showFriendListDialog());
            }

            @Override
            public void onFailure(Call<ApiResponse<Integer>> call, Throwable t) {
                tvFriendCount.setText("👥 Bạn bè: 0");
                tvFriendCount.setOnClickListener(v -> showFriendListDialog());
            }
        });
    }

    private void loadFriendStatusFromApi(long otherUserId) {
        friendApiService.getFriendStatus(otherUserId).enqueue(new Callback<ApiResponse<String>>() {
            @Override
            public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                String status = "NONE";
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    // Gson type erasure: result could be any object, force toString and clean
                    status = String.valueOf(response.body().getResult())
                            .trim().replace("\"", "");
                }
                android.util.Log.d("UserProfile", "Friend status for userId=" + otherUserId + ": [" + status + "]");
                setupFriendButton(status);
            }

            @Override
            public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                android.util.Log.e("UserProfile", "getFriendStatus failed", t);
                setupFriendButton("NONE");
            }
        });
    }

    private void lookupUserIdByName(String name) {
        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);

        userApi.searchByName(name).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    Map<String, Object> data = response.body().getResult();
                    if (data.get("id") != null) {
                        viewedUserId = ((Number) data.get("id")).longValue();
                        android.util.Log.d("UserProfile", "Resolved userId=" + viewedUserId + " from name=" + name);
                        loadFriendStatusFromApi(viewedUserId);
                        return;
                    }
                }
                android.util.Log.w("UserProfile", "Could not resolve userId for name=" + name);
                setupFriendButton("NONE");
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                android.util.Log.e("UserProfile", "searchByName failed", t);
                setupFriendButton("NONE");
            }
        });
    }

    private void setupFriendButton(String status) {
        switch (status) {
            case "BLOCKED":
                btnAddFriend.setText("Đã chặn");
                btnAddFriend.setEnabled(false);
                btnAddFriend.setAlpha(0.5f);
                btnMessage.setVisibility(View.GONE);
                tvInterestsTitle.setVisibility(View.GONE);
                chipGroupUserInterests.setVisibility(View.GONE);
                rvActivePostsProfile.setVisibility(View.GONE);
                tvNoActivePosts.setVisibility(View.GONE);
                tvReviewsTitle.setVisibility(View.GONE);
                rvUserReviews.setVisibility(View.GONE);
                break;

            case "FRIEND":
                btnAddFriend.setText("Bạn bè");
                btnAddFriend.setEnabled(true);
                btnAddFriend.setAlpha(1.0f);
                btnMessage.setVisibility(View.VISIBLE);
                btnAddFriend.setOnClickListener(v -> showFriendOptionsMenu());
                btnMessage.setOnClickListener(v -> {
                    if (viewedUserId <= 0) {
                        Toast.makeText(this, "Không thể nhắn tin", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Gọi API lấy hoặc tạo phòng DM
                    RetrofitClient.loadToken(this);
                    com.example.weconnect.api.ChatApiService chatApi =
                            RetrofitClient.getClient().create(com.example.weconnect.api.ChatApiService.class);
                    chatApi.getDirectRoom(viewedUserId).enqueue(new Callback<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call,
                                               Response<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getResult() != null) {
                                com.example.weconnect.models.ChatRoomApiResponse room = response.body().getResult();
                                Intent intent = new Intent(UserProfileActivity.this, ConversationActivity.class);
                                intent.putExtra("room_id", room.getId());
                                intent.putExtra("chat_name", username);
                                startActivity(intent);
                            } else {
                                String msg = "Không thể mở phòng chat";
                                if (response.body() != null && response.body().getMessage() != null) {
                                    msg = response.body().getMessage();
                                }
                                Toast.makeText(UserProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<com.example.weconnect.models.ChatRoomApiResponse>> call, Throwable t) {
                            Toast.makeText(UserProfileActivity.this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
                break;

            case "PENDING_SENT":
                btnAddFriend.setText("Đã gửi lời mời");
                btnAddFriend.setEnabled(true);
                btnAddFriend.setAlpha(0.8f);
                btnMessage.setVisibility(View.GONE);
                btnAddFriend.setOnClickListener(v -> {
                    // Hủy lời mời đã gửi
                    friendApiService.cancelFriend(viewedUserId).enqueue(new Callback<ApiResponse<Void>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(UserProfileActivity.this, "Đã hủy lời mời", Toast.LENGTH_SHORT).show();
                                setupFriendButton("NONE");
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                            Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
                break;

            case "PENDING_RECEIVED":
                btnAddFriend.setText("Phản hồi");
                btnAddFriend.setEnabled(true);
                btnAddFriend.setAlpha(1.0f);
                btnMessage.setVisibility(View.GONE);
                btnAddFriend.setOnClickListener(v -> showFriendResponseDialog());
                break;

            default: // NONE
                btnAddFriend.setText("+ Thêm bạn bè");
                btnAddFriend.setEnabled(true);
                btnAddFriend.setAlpha(1.0f);
                btnMessage.setVisibility(View.GONE);
                btnAddFriend.setOnClickListener(v -> {
                    if (viewedUserId <= 0) {
                        Toast.makeText(this, "Không thể thêm bạn bè", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    friendApiService.sendFriendRequest(viewedUserId).enqueue(new Callback<ApiResponse<Void>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(UserProfileActivity.this, "Đã gửi lời mời kết bạn", Toast.LENGTH_SHORT).show();
                                setupFriendButton("PENDING_SENT");
                            } else {
                                String errorMsg = "Không thể gửi lời mời kết bạn";
                                try {
                                    String errorBody = response.errorBody() != null ? response.errorBody().string() : "";
                                    android.util.Log.e("UserProfile", "sendFriendRequest error: code=" + response.code() + " body=" + errorBody);
                                    org.json.JSONObject json = new org.json.JSONObject(errorBody);
                                    if (json.has("message")) {
                                        errorMsg = json.getString("message");
                                    }
                                } catch (Exception ignored) {}
                                Toast.makeText(UserProfileActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                            Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
                break;
        }
    }

    private void showFriendListDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = buildIosRoot();
        makeSheetTransparent(root);

        // Group 1: friends list
        LinearLayout group1 = buildIosGroup();

        // Header
        TextView header = new TextView(this);
        header.setText("Danh sách bạn bè");
        header.setTextSize(13);
        header.setTextColor(0xFF8E8E93);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
        group1.addView(header, matchW());

        addIosSep(group1);

        // Loading row
        TextView tvLoading = new TextView(this);
        tvLoading.setText("Đang tải...");
        tvLoading.setTextSize(17);
        tvLoading.setTextColor(0xFF8E8E93);
        tvLoading.setGravity(Gravity.CENTER);
        tvLoading.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
        group1.addView(tvLoading, matchW());

        root.addView(group1, matchW());
        addGroupGap(root);

        // Group 2: Cancel
        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());

        sheet.setContentView(root);
        sheet.show();

        // Load từ backend
        friendApiService.getFriends().enqueue(new Callback<ApiResponse<java.util.List<java.util.Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<java.util.List<java.util.Map<String, Object>>>> call,
                                   Response<ApiResponse<java.util.List<java.util.Map<String, Object>>>> response) {
                group1.removeView(tvLoading);
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    java.util.List<java.util.Map<String, Object>> friendsList = response.body().getResult();
                    if (friendsList.isEmpty()) {
                        TextView tvEmpty = new TextView(UserProfileActivity.this);
                        tvEmpty.setText("Bạn chưa có bạn bè nào");
                        tvEmpty.setTextSize(17);
                        tvEmpty.setTextColor(0xFF8E8E93);
                        tvEmpty.setGravity(Gravity.CENTER);
                        tvEmpty.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
                        group1.addView(tvEmpty, matchW());
                    } else {
                        boolean first = true;
                        for (java.util.Map<String, Object> friend : friendsList) {
                            String friendName = friend.get("fullName") != null
                                    ? friend.get("fullName").toString() : "Người dùng";
                            long friendId = -1;
                            try {
                                if (friend.get("userId") != null)
                                    friendId = ((Number) friend.get("userId")).longValue();
                            } catch (Exception ignored) {}

                            if (!first) addIosSep(group1);
                            first = false;

                            LinearLayout row = new LinearLayout(UserProfileActivity.this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            row.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
                            row.setClickable(true);
                            row.setFocusable(true);
                            android.util.TypedValue ripple = new android.util.TypedValue();
                            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
                            row.setBackgroundResource(ripple.resourceId);

                            // Avatar placeholder circle 44dp
                            ImageView ivAvatar = new ImageView(UserProfileActivity.this);
                            int size = dpPx(44);
                            LinearLayout.LayoutParams avParams = new LinearLayout.LayoutParams(size, size);
                            ivAvatar.setLayoutParams(avParams);
                            ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
                            ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
                            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                            circle.setColor(0xFFE0E0E0);
                            ivAvatar.setBackground(circle);
                            ivAvatar.setClipToOutline(true);
                            row.addView(ivAvatar);

                            TextView tvName = new TextView(UserProfileActivity.this);
                            tvName.setText(friendName);
                            tvName.setTextSize(16);
                            tvName.setTextColor(0xFF1C1C1E);
                            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            nameParams.leftMargin = dpPx(12);
                            tvName.setLayoutParams(nameParams);
                            row.addView(tvName);

                            final long fId = friendId;
                            final String fName = friendName;
                            row.setOnClickListener(v -> {
                                sheet.dismiss();
                                Intent intent = new Intent(UserProfileActivity.this, UserProfileActivity.class);
                                intent.putExtra("username", fName);
                                intent.putExtra("user_id", fId);
                                intent.putExtra("view_other", true);
                                startActivity(intent);
                            });
                            group1.addView(row, matchW());
                        }
                    }
                } else {
                    tvLoading.setText("Không thể tải danh sách bạn bè");
                    group1.addView(tvLoading, matchW());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<java.util.List<java.util.Map<String, Object>>>> call, Throwable t) {
                group1.removeView(tvLoading);
                TextView tvErr = new TextView(UserProfileActivity.this);
                tvErr.setText("Lỗi kết nối");
                tvErr.setTextSize(17);
                tvErr.setTextColor(0xFF8E8E93);
                tvErr.setGravity(Gravity.CENTER);
                tvErr.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
                group1.addView(tvErr, matchW());
            }
        });
    }

    private void showFriendOptionsMenu() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = buildIosRoot();
        makeSheetTransparent(root);

        // Group 1: username header + actions
        LinearLayout group1 = buildIosGroup();

        TextView header = new TextView(this);
        header.setText(username);
        header.setTextSize(13);
        header.setTextColor(0xFF8E8E93);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
        group1.addView(header, matchW());

        addIosSep(group1);

        addIosRow(group1, "Huỷ kết bạn", 0xFFFF3B30, v -> {
            sheet.dismiss();
            new AlertDialog.Builder(this)
                    .setTitle("Huỷ kết bạn")
                    .setMessage("Bạn có chắc muốn huỷ kết bạn với " + username + "?")
                    .setPositiveButton("Huỷ kết bạn", (d, w) -> {
                        if (viewedUserId > 0) {
                            friendApiService.unfriend(viewedUserId).enqueue(new Callback<ApiResponse<Void>>() {
                                @Override
                                public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                                    if (response.isSuccessful()) {
                                        Toast.makeText(UserProfileActivity.this, "Đã huỷ kết bạn", Toast.LENGTH_SHORT).show();
                                        setupFriendButton("NONE");
                                    }
                                }
                                @Override
                                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                    Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    })
                    .setNegativeButton("Không", null)
                    .show();
        });

        addIosSep(group1);

        addIosRow(group1, "Chặn người dùng", 0xFFFF3B30, v -> {
            sheet.dismiss();
            new AlertDialog.Builder(this)
                    .setTitle("Chặn người dùng")
                    .setMessage("Bạn có chắc muốn chặn " + username + "? Người này sẽ không thể liên hệ với bạn.")
                    .setPositiveButton("Chặn", (d, w) -> {
                        if (viewedUserId > 0) {
                            friendApiService.blockUser(viewedUserId).enqueue(new Callback<ApiResponse<Void>>() {
                                @Override
                                public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                                    if (response.isSuccessful()) {
                                        Toast.makeText(UserProfileActivity.this, "Đã chặn người dùng", Toast.LENGTH_SHORT).show();
                                        setupFriendButton("BLOCKED");
                                    }
                                }
                                @Override
                                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                    Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    })
                    .setNegativeButton("Không", null)
                    .show();
        });

        root.addView(group1, matchW());
        addGroupGap(root);

        // Group 2: Cancel
        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());

        sheet.setContentView(root);
        sheet.show();
    }

    private void showRateUserDialog() {
        long targetId = getIntent().getLongExtra("user_id", -1);
        if (targetId <= 0) {
            Toast.makeText(this, "Không thể đánh giá người dùng này", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch common activities trước, rồi show dialog
        Toast.makeText(this, "Đang tải hoạt động chung...", Toast.LENGTH_SHORT).show();

        reviewApiService.getCommonActivities(targetId).enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                List<String> activityNames = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    for (Map<String, Object> item : response.body().getResult()) {
                        String name = item.get("activityName") != null
                                ? item.get("activityName").toString() : "";
                        String tag = item.get("interestTag") != null
                                ? item.get("interestTag").toString() : "";
                        // Ghép tag + content cho rõ ràng
                        String display = tag.isEmpty() ? name : "[" + tag + "] " + name;
                        if (!display.isEmpty()) {
                            activityNames.add(display);
                        }
                    }
                }

                if (activityNames.isEmpty()) {
                    Toast.makeText(UserProfileActivity.this,
                            "Không tìm thấy hoạt động chung", Toast.LENGTH_SHORT).show();
                    return;
                }

                showRateUserDialogWithActivities(activityNames);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRateUserDialogWithActivities(List<String> activityNames) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        root.setPadding(64, 48, 64, 48);

        // Header
        TextView header = new TextView(this);
        header.setText("Đánh giá " + username);
        header.setTextSize(13);
        header.setTextColor(0xFF8E8E93);
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
        btnSubmit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF007AFF));
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
        long reviewedUserId = getIntent().getLongExtra("user_id", -1);
        if (reviewedUserId <= 0) {
            Toast.makeText(this, "Không thể gửi đánh giá", Toast.LENGTH_SHORT).show();
            return;
        }

        // Map stars to reputation label
        String[] labels = {"Cần cải thiện", "Trung bình", "Tích cực", "Đáng tin cậy", "Xuất sắc"};
        String reputationLabel = labels[Math.min(stars - 1, labels.length - 1)];

        Map<String, Object> body = new HashMap<>();
        body.put("reviewedUserId", reviewedUserId);
        body.put("activityName", activityName);
        body.put("reputationLabel", reputationLabel);
        body.put("comment", comment.isEmpty() ? "Đánh giá " + stars + " sao" : comment);

        reviewApiService.createReview(body).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(UserProfileActivity.this,
                            "Đã gửi đánh giá " + stars + " sao cho " + username,
                            Toast.LENGTH_SHORT).show();
                    // Refresh reviews
                    loadReviewsFromBackend();
                } else {
                    String errorMsg = "Không thể gửi đánh giá";
                    try {
                        if (response.errorBody() != null) {
                            String errorJson = response.errorBody().string();
                            org.json.JSONObject json = new org.json.JSONObject(errorJson);
                            if (json.has("message")) {
                                errorMsg = json.getString("message");
                            }
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(UserProfileActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadReviewsFromBackend() {
        long targetUserId = getIntent().getLongExtra("user_id", -1);
        if (targetUserId <= 0) {
            android.content.SharedPreferences prefs = getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
            targetUserId = prefs.getLong("user_id", -1);
        }
        if (targetUserId <= 0) {
            // Fallback: show empty
            rvUserReviews.setAdapter(new UserReviewAdapter(new ArrayList<>()));
            return;
        }

        reviewApiService.getReviews(targetUserId).enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    List<Map<String, Object>> reviewMaps = response.body().getResult();
                    List<UserReview> reviews = new ArrayList<>();
                    for (Map<String, Object> map : reviewMaps) {
                        String reviewerName = map.get("reviewerName") != null ? map.get("reviewerName").toString() : "Ẩn danh";
                        String activityName = map.get("activityName") != null ? map.get("activityName").toString() : "";
                        String reputationLabel = map.get("reputationLabel") != null ? map.get("reputationLabel").toString() : "";
                        String reviewComment = map.get("comment") != null ? map.get("comment").toString() : "";
                        reviews.add(new UserReview(reviewerName, activityName, reputationLabel, reviewComment));
                    }
                    rvUserReviews.setAdapter(new UserReviewAdapter(reviews));
                } else {
                    rvUserReviews.setAdapter(new UserReviewAdapter(new ArrayList<>()));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                rvUserReviews.setAdapter(new UserReviewAdapter(new ArrayList<>()));
            }
        });
    }

    private void showFriendResponseDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = buildIosRoot();
        makeSheetTransparent(root);

        // Group 1: header + actions
        LinearLayout group1 = buildIosGroup();

        TextView header = new TextView(this);
        header.setText(username + " muốn kết bạn với bạn");
        header.setTextSize(13);
        header.setTextColor(0xFF8E8E93);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
        group1.addView(header, matchW());

        addIosSep(group1);

        addIosRow(group1, "Xác nhận", 0xFF1C1C1E, v -> {
            sheet.dismiss();
            friendApiService.acceptFriend(viewedUserId).enqueue(new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(UserProfileActivity.this, "Đã chấp nhận kết bạn!", Toast.LENGTH_SHORT).show();
                        setupFriendButton("FRIEND");
                    }
                }
                @Override
                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                    Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                }
            });
        });

        addIosSep(group1);

        addIosRow(group1, "Từ chối", 0xFFFF3B30, v -> {
            sheet.dismiss();
            friendApiService.declineFriend(viewedUserId).enqueue(new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(UserProfileActivity.this, "Đã từ chối lời mời", Toast.LENGTH_SHORT).show();
                        setupFriendButton("NONE");
                    }
                }
                @Override
                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                    Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                }
            });
        });

        root.addView(group1, matchW());
        addGroupGap(root);

        // Group 2: Cancel
        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());

        sheet.setContentView(root);
        sheet.show();
    }

    private void showBlockUserConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Chặn người dùng")
                .setMessage("Bạn có chắc muốn chặn " + username + "? Người này sẽ không thể nhắn tin hoặc xem bài viết của bạn.")
                .setPositiveButton("Chặn", (d, w) -> {
                    if (viewedUserId <= 0) {
                        Toast.makeText(this, "Không thể chặn người dùng này", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    friendApiService.blockUser(viewedUserId).enqueue(new Callback<ApiResponse<Void>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(UserProfileActivity.this, "Đã chặn " + username, Toast.LENGTH_SHORT).show();
                                setupFriendButton("BLOCKED");
                            } else {
                                Toast.makeText(UserProfileActivity.this, "Không thể chặn người dùng", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                            Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void showReportUserDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = buildIosRoot();
        makeSheetTransparent(root);

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

        // Group 1: header + reason rows
        LinearLayout group1 = buildIosGroup();

        TextView header = new TextView(this);
        header.setText("Báo cáo " + username);
        header.setTextSize(13);
        header.setTextColor(0xFF8E8E93);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
        group1.addView(header, matchW());

        for (int i = 0; i < reasons.length; i++) {
            final int index = i;
            addIosSep(group1);
            TextView tvReason = new TextView(this);
            tvReason.setText(reasons[i]);
            tvReason.setTextSize(17);
            tvReason.setTextColor(0xFF1C1C1E);
            tvReason.setGravity(Gravity.CENTER);
            tvReason.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
            tvReason.setClickable(true);
            tvReason.setFocusable(true);
            android.util.TypedValue ripple = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            tvReason.setBackgroundResource(ripple.resourceId);
            tvReason.setOnClickListener(v -> {
                if (selectedIndex[0] >= 0) {
                    reasonViews[selectedIndex[0]].setTextColor(0xFF1C1C1E);
                }
                selectedIndex[0] = index;
                tvReason.setTextColor(0xFF007AFF);
            });
            reasonViews[i] = tvReason;
            group1.addView(tvReason, matchW());
        }

        root.addView(group1, matchW());
        addGroupGap(root);

        // Group 2: description EditText
        LinearLayout group2 = buildIosGroup();
        EditText etDescription = new EditText(this);
        etDescription.setHint("Nhập mô tả chi tiết...");
        etDescription.setTextSize(14);
        etDescription.setMinLines(2);
        etDescription.setMaxLines(4);
        etDescription.setBackground(null);
        etDescription.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
        group2.addView(etDescription, matchW());
        root.addView(group2, matchW());
        addGroupGap(root);

        // Group 3: Submit
        LinearLayout group3 = buildIosGroup();
        addIosRow(group3, "Gửi báo cáo", 0xFF007AFF, v -> {
            if (selectedIndex[0] < 0) {
                Toast.makeText(this, "Vui lòng chọn lý do báo cáo", Toast.LENGTH_SHORT).show();
                return;
            }
            sheet.dismiss();
            submitReportToBackend("USER", viewedUserId, reasons[selectedIndex[0]],
                    etDescription.getText().toString().trim());
        });
        root.addView(group3, matchW());
        addGroupGap(root);

        // Group 4: Cancel
        LinearLayout group4 = buildIosGroup();
        addIosRow(group4, "Huỷ", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group4, matchW());

        sheet.setContentView(root);
        sheet.show();
    }

    private void submitReportToBackend(String targetType, long targetId,
                                        String reason, String description) {
        RetrofitClient.loadToken(this);
        com.example.weconnect.api.ReportApiService reportApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.ReportApiService.class);

        Map<String, Object> body = new HashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("reason", reason);
        body.put("description", description);

        reportApi.createReport(body).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(UserProfileActivity.this,
                            "Đã gửi báo cáo. Cảm ơn bạn!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(UserProfileActivity.this,
                            "Không thể gửi báo cáo. Thử lại sau.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(UserProfileActivity.this,
                        "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSavedAvatar() {
        // Prefer server-side avatar URL (updated after each upload)
        String serverUrl = RetrofitClient.getAvatarUrl(this);
        if (serverUrl != null && !serverUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(serverUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(ivUserProfileAvatar);
            return;
        }
        // Fallback: legacy local file (first-time use before first server upload)
        String path = getSharedPreferences("weconnect_prefs", MODE_PRIVATE)
                .getString("user_avatar_uri", null);
        if (path != null) {
            try {
                java.io.File file = new java.io.File(path);
                if (file.exists()) {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path);
                    if (bitmap != null) {
                        ivUserProfileAvatar.setImageBitmap(bitmap);
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }
        ivUserProfileAvatar.setImageResource(R.drawable.ic_user_placeholder);
    }

    private void showAvatarOptionsSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = buildIosRoot();
        makeSheetTransparent(root);

        // Group 1: header + actions
        LinearLayout group1 = buildIosGroup();

        TextView header = new TextView(this);
        header.setText("Ảnh đại diện");
        header.setTextSize(13);
        header.setTextColor(0xFF8E8E93);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
        group1.addView(header, matchW());

        addIosSep(group1);

        addIosRow(group1, "Xem ảnh đại diện", 0xFF1C1C1E, v -> {
            sheet.dismiss();
            Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            ImageView imageView = new ImageView(this);
            imageView.setImageDrawable(ivUserProfileAvatar.getDrawable());
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(Color.BLACK);
            imageView.setOnClickListener(v2 -> dialog.dismiss());
            dialog.setContentView(imageView);
            dialog.show();
        });

        addIosSep(group1);

        addIosRow(group1, "Chọn ảnh từ thư viện", 0xFF1C1C1E, v -> {
            sheet.dismiss();
            Intent pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
            pickIntent.setType("image/*");
            pickIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pickIntent, 1001);
        });

        root.addView(group1, matchW());
        addGroupGap(root);

        // Group 2: Cancel
        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());

        sheet.setContentView(root);
        sheet.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 3001 && resultCode == RESULT_OK) {
            // Sau khi EditProfile lưu thành công → reload lại profile để UI cập nhật
            loadOwnProfileName();
            return;
        }
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            android.net.Uri selectedImage = data.getData();
            if (selectedImage != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            selectedImage, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                uploadAvatarToServer(selectedImage);
            }
        } else if (requestCode == 2001 && resultCode == RESULT_OK && data != null) {
            long editPostId = data.getLongExtra("edit_post_id", -1);
            if (editPostId != -1) {
                updatePostViaApi(editPostId, data);
            }
        }
    }

    private void uploadAvatarToServer(android.net.Uri uri) {
        try {
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "Không thể đọc ảnh", Toast.LENGTH_SHORT).show();
                return;
            }
            byte[] bytes = readAllBytesProfile(is);
            is.close();

            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "image/jpeg";
            String ext = mimeType.contains("png") ? ".png" : mimeType.contains("webp") ? ".webp" : ".jpg";
            String fileName = "avatar_" + System.currentTimeMillis() + ext;

            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse(mimeType), bytes);
            okhttp3.MultipartBody.Part filePart =
                    okhttp3.MultipartBody.Part.createFormData("file", fileName, requestBody);

            ivUserProfileAvatar.setAlpha(0.5f);

            postApiService.uploadImage(filePart).enqueue(new Callback<ApiResponse<String>>() {
                @Override
                public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                    ivUserProfileAvatar.setAlpha(1.0f);
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        String avatarUrl = response.body().getResult();
                        if (avatarUrl != null && avatarUrl.startsWith("/")) {
                            avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                        }
                        // Persist URL globally so all screens stay in sync
                        RetrofitClient.saveAvatarUrl(UserProfileActivity.this, avatarUrl);
                        // Push new avatarUrl to backend profile
                        updateProfileAvatarOnBackend(avatarUrl);
                        // Wipe Glide cache so every screen shows fresh avatar on next load
                        com.bumptech.glide.Glide.get(UserProfileActivity.this).clearMemory();
                        new Thread(() -> com.bumptech.glide.Glide.get(UserProfileActivity.this).clearDiskCache()).start();
                        // Reload own avatar ImageView immediately, bypassing old cache
                        final String finalUrl = avatarUrl;
                        com.bumptech.glide.Glide.with(UserProfileActivity.this)
                                .load(finalUrl)
                                .placeholder(R.drawable.ic_user_placeholder)
                                .error(R.drawable.ic_user_placeholder)
                                .skipMemoryCache(true)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                .circleCrop()
                                .into(ivUserProfileAvatar);
                        Toast.makeText(UserProfileActivity.this,
                                "Đã cập nhật ảnh đại diện!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(UserProfileActivity.this,
                                "Lỗi khi tải ảnh lên server", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                    ivUserProfileAvatar.setAlpha(1.0f);
                    Toast.makeText(UserProfileActivity.this,
                            "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi đọc ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateProfileAvatarOnBackend(String avatarUrl) {
        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);
        Map<String, Object> body = new HashMap<>();
        body.put("avatarUrl", avatarUrl);
        userApi.updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {}
            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {}
        });
    }

    // ── iOS action-sheet helpers ────────────────────────────────────────────

    private LinearLayout buildIosRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0x00000000);
        int p = dpPx(10);
        root.setPadding(p, 0, p, p);
        return root;
    }

    private LinearLayout buildIosGroup() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dpPx(14));
        ll.setBackground(bg);
        ll.setClipToOutline(true);
        return ll;
    }

    private void addIosRow(LinearLayout parent, String text, int color, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
        if (listener != null) {
            tv.setClickable(true);
            tv.setFocusable(true);
            android.util.TypedValue tv2 = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv2, true);
            tv.setBackgroundResource(tv2.resourceId);
            tv.setOnClickListener(listener);
        }
        parent.addView(tv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addIosSep(LinearLayout parent) {
        View sep = new View(this);
        sep.setBackgroundColor(0xFFD1D1D6);
        sep.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        parent.addView(sep);
    }

    private void addGroupGap(LinearLayout parent) {
        View gap = new View(this);
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
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ────────────────────────────────────────────────────────────────────────

    private void updatePostViaApi(long postId, Intent data) {
        String imageUri = data.getStringExtra("post_image_uri");
        if (imageUri != null && imageUri.startsWith("content://")) {
            try {
                android.net.Uri uri = android.net.Uri.parse(imageUri);
                java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    byte[] bytes = readAllBytesProfile(inputStream);
                    inputStream.close();
                    String mimeType = getContentResolver().getType(uri);
                    if (mimeType == null) mimeType = "image/jpeg";
                    String ext = mimeType.contains("png") ? ".png" : mimeType.contains("webp") ? ".webp" : ".jpg";
                    String fileName = "post_image_" + System.currentTimeMillis() + ext;
                    okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                            okhttp3.MediaType.parse(mimeType), bytes);
                    okhttp3.MultipartBody.Part filePart =
                            okhttp3.MultipartBody.Part.createFormData("file", fileName, requestBody);

                    postApiService.uploadImage(filePart).enqueue(new Callback<ApiResponse<String>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                            String serverUrl = null;
                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                serverUrl = response.body().getResult();
                            }
                            sendUpdatePostProfile(postId, data, serverUrl);
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                            sendUpdatePostProfile(postId, data, null);
                        }
                    });
                    return;
                }
            } catch (Exception e) {
                android.util.Log.e("UPLOAD_IMAGE", "Error: " + e.getMessage());
            }
        }
        sendUpdatePostProfile(postId, data, imageUri);
    }

    private void sendUpdatePostProfile(long postId, Intent data, String imageUrl) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("content", data.getStringExtra("post_content"));
        body.put("interestTag", data.getStringExtra("post_tag"));
        body.put("location", data.getStringExtra("post_location"));
        body.put("maxMembers", data.getIntExtra("post_max_members", 10));
        if (imageUrl != null) body.put("imageUrl", imageUrl);

        long endTimeMillis = data.getLongExtra("post_end_time", System.currentTimeMillis() + 24L * 60L * 60L * 1000L);
        java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
        body.put("startTime", isoFormat.format(new java.util.Date()));
        body.put("endTime", isoFormat.format(new java.util.Date(endTimeMillis)));

        RetrofitClient.loadToken(this);
        com.example.weconnect.api.PostApiService postApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.PostApiService.class);
        postApi.updatePost(postId, body).enqueue(new Callback<ApiResponse<com.example.weconnect.models.PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<com.example.weconnect.models.PostResponse>> call,
                                   Response<ApiResponse<com.example.weconnect.models.PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(UserProfileActivity.this, "Đã cập nhật bài viết!", Toast.LENGTH_SHORT).show();
                    bindActivePosts();
                } else {
                    Toast.makeText(UserProfileActivity.this, "Không thể cập nhật bài viết", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<com.example.weconnect.models.PostResponse>> call, Throwable t) {
                Toast.makeText(UserProfileActivity.this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
