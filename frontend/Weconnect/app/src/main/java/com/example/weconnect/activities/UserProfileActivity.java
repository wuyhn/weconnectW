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
import com.example.weconnect.utils.DirectMessageHelper;
import com.example.weconnect.utils.InterestTextUtils;
import com.example.weconnect.utils.ReviewReportBottomSheet;
import com.example.weconnect.utils.UserReportBottomSheet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    private TextView tvReputationLabel;
    private TextView tvUserBio;
    private TextView tvUserBirthday;
    private TextView tvUserGender;
    private TextView tvUserProvince;
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
    private com.google.android.material.bottomnavigation.BottomNavigationView footerNavigationProfile;

    private DrawerLayout drawerLayoutProfile;
    private LinearLayout menuEditProfile;
    private LinearLayout menuChangePassword;
    private LinearLayout menuDeleteAccount;

    private RecyclerView rvActivePostsProfile;
    private View tvNoActivePosts;
    private TextView tvInterestsTitle;
    private com.google.android.material.tabs.TabLayout tabLayoutProfile;
    private LinearLayout containerMyPosts;
    private LinearLayout containerMyActivities;

    private View cardCreatePostProfile;
    private TextView tvCreatePostHint;
    private TextView tvReviewsTitle;

    // Summary card điểm uy tín
    private View cardReputationSummary;
    private TextView tvSummaryReputation;
    private TextView tvSummaryAvgRating;
    private TextView tvSummaryReviewCount;
    private int summaryReputationVal = 0;
    private float summaryAvgRatingVal = 0f;

    // Cache: tab data + other-user avatar (preserved across onResume without extra API calls)
    private List<Post> cachedMyPosts = null;
    private List<Post> cachedMyActivities = null;
    private String cachedOtherAvatarUrl = null;
    private PostAdapter myPostsAdapter = null;
    private PostAdapter myActivitiesAdapter = null;

    // Related posts (from other users matching interest tags)
    private TextView tvRelatedPostsTitle;
    private TextView tvNoRelatedPosts;
    private RecyclerView rvRelatedPosts;

    private String username;
    private long viewedUserId = -1; // ID của user đang xem (dùng cho friend API)
    private boolean blockProfileMode = false;
    private boolean isBlockedByMe = false;
    private boolean hasBlockedMe = false;
    private boolean isBlockedBetweenUsers = false;
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
        blockProfileMode = getIntent().getBooleanExtra("blocked_profile", false);
        isBlockedByMe = getIntent().getBooleanExtra("is_blocked_by_me", false);
        hasBlockedMe = getIntent().getBooleanExtra("has_blocked_me", false);
        isBlockedBetweenUsers = blockProfileMode || isBlockedByMe || hasBlockedMe;
        bindFakeUserProfile();
        setupClickListeners();
        bindSocialState();
        setupDrawerMenu();
        setupProfileTabs();
        if (!blockProfileMode) {
            bindActivePosts();
            loadMyActivities();
        }
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
                        String activityStartIso = data.getStringExtra("post_activity_start_iso");
                        String activityEndIso = data.getStringExtra("post_activity_end_iso");
                        createPostViaApi(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso);
                    }
                }
        );
    }

    private void createPostViaApi(String content, String tag, String location,
                                  int maxMembers, String imageUri, long endTimeMillis,
                                  String activityStartIso, String activityEndIso) {
        if (imageUri != null) {
            uploadImageThenCreatePost(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso);
        } else {
            sendCreatePostProfile(content, tag, location, maxMembers, null, endTimeMillis, activityStartIso, activityEndIso);
        }
    }

    private void uploadImageThenCreatePost(String content, String tag, String location,
                                           int maxMembers, String imageUri, long endTimeMillis,
                                           String activityStartIso, String activityEndIso) {
        try {
            android.net.Uri uri = android.net.Uri.parse(imageUri);
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                sendCreatePostProfile(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso);
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
                    sendCreatePostProfile(content, tag, location, maxMembers, serverUrl, endTimeMillis, activityStartIso, activityEndIso);
                }
                @Override
                public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                    sendCreatePostProfile(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso);
                }
            });
        } catch (Exception e) {
            sendCreatePostProfile(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso);
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
                                       int maxMembers, String imageUrl, long endTimeMillis,
                                       String activityStartIso, String activityEndIso) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("interestTag", tag);
        body.put("location", location);
        body.put("maxMembers", maxMembers);
        if (imageUrl != null) {
            body.put("imageUrl", imageUrl);
        }
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        body.put("startTime", activityStartIso != null ? activityStartIso : isoFormat.format(new Date()));
        body.put("endTime", isoFormat.format(new Date(endTimeMillis)));
        if (activityEndIso != null) {
            body.put("activityEndTime", activityEndIso);
        }

        postApiService.createPost(body).enqueue(new Callback<ApiResponse<PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PostResponse>> call,
                                   Response<ApiResponse<PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(UserProfileActivity.this, "Đã tạo bài đăng!", Toast.LENGTH_SHORT).show();
                    cachedMyPosts = null;
                    myPostsAdapter = null;
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
        applyNavBadge();
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        if (blockProfileMode) {
            showBlockedProfileState();
            return;
        }
        bindSocialState();
        // Reload cached avatar for other user (no API call needed)
        if (viewOther && cachedOtherAvatarUrl != null && !cachedOtherAvatarUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(cachedOtherAvatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(ivUserProfileAvatar);
        }
        cachedMyPosts = null;
        myPostsAdapter = null;
        bindActivePosts();
        loadMyActivities();
        hideRelatedPosts();
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
        tvReputationLabel = findViewById(R.id.tvReputationLabel);
        tvUserBio = findViewById(R.id.tvUserBio);
        tvUserBirthday = findViewById(R.id.tvUserBirthday);
        tvUserGender = findViewById(R.id.tvUserGender);
        tvUserProvince = findViewById(R.id.tvUserProvince);
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

        drawerLayoutProfile = findViewById(R.id.drawerLayoutProfile);
        menuEditProfile = findViewById(R.id.menuEditProfile);
        menuChangePassword = findViewById(R.id.menuChangePassword);
        menuDeleteAccount = findViewById(R.id.menuDeleteAccount);

        rvActivePostsProfile = findViewById(R.id.rvActivePostsProfile);
        tvNoActivePosts = findViewById(R.id.tvNoActivePosts);
        tvInterestsTitle = findViewById(R.id.tvInterestsTitle);
        tabLayoutProfile = findViewById(R.id.tabLayoutProfile);
        containerMyPosts = findViewById(R.id.containerMyPosts);
        containerMyActivities = findViewById(R.id.containerMyActivities);

        cardCreatePostProfile = findViewById(R.id.cardCreatePostProfile);
        tvCreatePostHint = findViewById(R.id.tvCreatePostHint);
        tvReviewsTitle = findViewById(R.id.tvReviewsTitle);
        cardReputationSummary = findViewById(R.id.cardReputationSummary);
        tvSummaryReputation = findViewById(R.id.tvReputationScore);
        tvSummaryAvgRating = findViewById(R.id.tvAvgRating);
        tvSummaryReviewCount = findViewById(R.id.tvReviewCount);
        tvRelatedPostsTitle = findViewById(R.id.tvRelatedPostsTitle);
        tvNoRelatedPosts = findViewById(R.id.tvNoRelatedPosts);
        rvRelatedPosts = findViewById(R.id.rvRelatedPosts);
    }

    private void setupProfileTabs() {
        com.google.android.material.tabs.TabLayout tabLayout = findViewById(R.id.tabLayoutProfile);
        LinearLayout containerMyPosts = findViewById(R.id.containerMyPosts);
        LinearLayout containerMyActivities = findViewById(R.id.containerMyActivities);
        androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.nestedScrollProfile);

        if (tabLayout == null) return;

        // Per-tab scroll positions — index 0 = Bài viết, 1 = Hoạt động
        final int[] tabScrollY = {0, 0};
        final int[] currentTab = {0};

        tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                int newTab = tab.getPosition();
                if (newTab == currentTab[0]) return;

                if (scrollView != null) {
                    // Save outgoing tab's scroll
                    tabScrollY[currentTab[0]] = scrollView.getScrollY();
                    // First visit to new tab: inherit current position so screen doesn't jump
                    if (tabScrollY[newTab] == 0) {
                        tabScrollY[newTab] = scrollView.getScrollY();
                    }
                }
                currentTab[0] = newTab;

                if (newTab == 0) {
                    if (containerMyPosts != null) containerMyPosts.setVisibility(View.VISIBLE);
                    if (containerMyActivities != null) containerMyActivities.setVisibility(View.GONE);
                } else {
                    if (containerMyPosts != null) containerMyPosts.setVisibility(View.GONE);
                    if (containerMyActivities != null) containerMyActivities.setVisibility(View.VISIBLE);
                }

                // Restore scroll after layout pass
                if (scrollView != null) {
                    final int targetY = tabScrollY[newTab];
                    scrollView.post(() -> scrollView.scrollTo(0, targetY));
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
        if (footerNavigationProfile != null) {
            footerNavigationProfile.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    return true;
                } else if (id == R.id.nav_messages) {
                    Intent intent = new Intent(this, ChatListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    return true;
                } else if (id == R.id.nav_notifications) {
                    Intent intent = new Intent(this, NotificationsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    return true;
                } else if (id == R.id.nav_profile) {
                    return true;
                }
                return false;
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
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc muốn đăng xuất?")
                    .setPositiveButton("Đăng xuất", (d, w) -> {
                        WebSocketManager.getInstance().disconnect();
                        RetrofitClient.clearSession(this);
                        FakeSocialRepository.resetInstance();
                        com.example.weconnect.data.FakePostRepository.resetInstance();
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
        if (!viewOther) {
            tvUserReputation.setText(String.valueOf(Math.round(RetrofitClient.getReputationScore(this))));
        } else {
            tvUserReputation.setText("—");
        }

        if (blockProfileMode) {
            showBlockedProfileState();
            return;
        }
        
        rvUserReviews.setLayoutManager(new LinearLayoutManager(this));
        // Load reviews from backend
        loadReviewsFromBackend();

        // Load interests from backend
        loadInterestsFromBackend();

        // Load tên thật từ backend
        if (viewOther && viewedUserId > 0) {
            // Xem profile người khác: load đúng người theo userId
            loadOtherUserProfile(viewedUserId);
        } else if (!viewOther) {
            // Xem profile của chính mình
            loadOwnProfileName();
        }
        // else: view_other=true nhưng không có userId hợp lệ — giữ nguyên placeholder, không load sai profile
    }

    private void loadOtherUserProfile(long userId) {
        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);

        userApi.getUserProfile(userId).enqueue(new Callback<ApiResponse<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<java.util.Map<String, Object>>> call,
                                   Response<ApiResponse<java.util.Map<String, Object>>> response) {
                // Kịch bản 2: Tài khoản được xem đang bị khóa tạm thời.
                // Backend trả về HTTP 423 kèm lockUntil — hiển thị dialog và ngắt luồng điều hướng.
                if (response.code() == 423) {
                    showLockedAccountDialog(response.errorBody());
                    return;
                }

                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    java.util.Map<String, Object> profile = response.body().getResult();
                    isBlockedByMe = asBoolean(profile.get("isBlockedByMe"));
                    hasBlockedMe = asBoolean(profile.get("hasBlockedMe"));
                    isBlockedBetweenUsers = asBoolean(profile.get("isBlockedBetweenUsers"));
                    if (isBlockedBetweenUsers) {
                        blockProfileMode = true;
                        showBlockedProfileState();
                        return;
                    }
                    String fullName = profile.get("fullName") != null
                            ? profile.get("fullName").toString() : null;
                    if (fullName != null && !fullName.isEmpty()) {
                        username = fullName;
                        tvUserProfileName.setText(fullName);
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

                    String provinceId = profile.get("provinceId") != null
                            ? profile.get("provinceId").toString() : "";
                    String provinceName = profile.get("provinceName") != null
                            ? profile.get("provinceName").toString() : "";
                    if (tvUserProvince != null) {
                        tvUserProvince.setText(provinceName.isEmpty() ? "" : "📍 " + provinceName);
                        tvUserProvince.setVisibility(provinceName.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    // Điểm uy tín luôn hiển thị theo reputationScore hiện tại, kể cả khi user chưa có review.
                    if (tvUserReputation != null) {
                        Object repObj = profile.get("reputationScore");
                        int rep = repObj != null
                                ? (int) Math.round(((Number) repObj).doubleValue())
                                : 60;
                        tvUserReputation.setText(String.valueOf(rep));
                        if (tvReputationLabel != null) tvReputationLabel.setText("🏆 Điểm uy tín");
                        summaryReputationVal = rep;
                    }
                    Object avgRObj = profile.get("averageRating");
                    summaryAvgRatingVal = avgRObj != null ? ((Number) avgRObj).floatValue() : 0f;

                    // Load avatar with Glide
                    String avatarUrl = profile.get("avatarUrl") != null
                            ? profile.get("avatarUrl").toString() : null;
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        String displayUrl = resolveAvatarUrl(avatarUrl);
                        cachedOtherAvatarUrl = displayUrl;
                        com.bumptech.glide.Glide.with(UserProfileActivity.this)
                                .load(displayUrl)
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

    /**
     * Kịch bản 2: Hiển thị dialog thông báo khi user bị khóa tạm thời (HTTP 423).
     * Parse lockUntil từ error body và hiển thị ngày mở khóa dưới dạng dd/MM/yyyy.
     * Sau khi user bấm "Đã hiểu", đóng Activity — luồng điều hướng vào profile bị ngắt.
     */
    private void showLockedAccountDialog(okhttp3.ResponseBody errorBody) {
        String lockDateStr = "không xác định";
        try {
            if (errorBody != null) {
                String bodyStr = errorBody.string();
                org.json.JSONObject json = new org.json.JSONObject(bodyStr);
                org.json.JSONObject result = json.optJSONObject("result");
                if (result != null) {
                    String lockUntil = result.optString("lockUntil", "");
                    if (!lockUntil.isEmpty()) {
                        // Parse ISO-8601: "2026-06-06T10:30:00" → format dd/MM/yyyy
                        java.text.SimpleDateFormat inputFmt = new java.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                        java.text.SimpleDateFormat outputFmt = new java.text.SimpleDateFormat(
                                "dd/MM/yyyy", java.util.Locale.getDefault());
                        java.util.Date date = inputFmt.parse(lockUntil);
                        if (date != null) {
                            lockDateStr = outputFmt.format(date);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Giữ nguyên "không xác định" nếu parse thất bại
        }

        final String finalDate = lockDateStr;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Tài khoản bị khóa")
                .setMessage("User này đã bị khóa tài khoản do vi phạm tiêu chuẩn cộng đồng.\n"
                        + "Tài khoản sẽ hoạt động trở lại vào ngày " + finalDate + ".")
                .setPositiveButton("Đã hiểu", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void loadOwnProfileName() {
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        if (viewOther) return;

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

                    String provinceId = profile.get("provinceId") != null
                            ? profile.get("provinceId").toString() : "";
                    String provinceName = profile.get("provinceName") != null
                            ? profile.get("provinceName").toString() : "";
                    if (tvUserProvince != null) {
                        tvUserProvince.setText(provinceName.isEmpty() ? "" : "📍 " + provinceName);
                        tvUserProvince.setVisibility(provinceName.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    // Điểm uy tín luôn hiển thị theo reputationScore hiện tại, kể cả khi user chưa có review.
                    RetrofitClient.saveUserProvince(UserProfileActivity.this, provinceId, provinceName);

                    if (tvUserReputation != null) {
                        Object repObj = profile.get("reputationScore");
                        int rep = repObj != null
                                ? (int) Math.round(((Number) repObj).doubleValue())
                                : 60;
                        tvUserReputation.setText(String.valueOf(rep));
                        RetrofitClient.saveReputationScore(UserProfileActivity.this, rep);
                        if (tvReputationLabel != null) tvReputationLabel.setText("🏆 Điểm uy tín");
                        summaryReputationVal = rep;
                    }
                    Object avgRObj2 = profile.get("averageRating");
                    summaryAvgRatingVal = avgRObj2 != null ? ((Number) avgRObj2).floatValue() : 0f;

                    // Load avatar with Glide + persist URL globally
                    String avatarUrl = profile.get("avatarUrl") != null
                            ? profile.get("avatarUrl").toString() : null;
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        RetrofitClient.saveAvatarUrl(UserProfileActivity.this, avatarUrl);
                        String displayUrl = resolveAvatarUrl(avatarUrl);
                        com.bumptech.glide.Glide.with(UserProfileActivity.this)
                                .load(displayUrl)
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
                            String trimmed = InterestTextUtils.stripLeadingIcon(tag);
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

    private String resolveAvatarUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        if (url.startsWith("/")) {
            return RetrofitClient.getBaseUrl() + url.substring(1);
        }
        // Xử lý full URL với IP cũ (emulator/session khác) → đổi sang BASE_URL hiện tại
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                java.net.URL parsed = new java.net.URL(url);
                String path = parsed.getPath();
                if (path != null && path.startsWith("/uploads/")) {
                    return RetrofitClient.getBaseUrl() + path.substring(1);
                }
            } catch (Exception ignored) {}
        }
        return url;
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
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

        // Determine if viewing own profile or someone else's — compare by ID, not by name
        boolean isOwnProfile = !getIntent().getBooleanExtra("view_other", false);

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
            defaultTags.add("Cà phê");
            defaultTags.add("Giao lưu");
            displayInterests(defaultTags);
        }
    }

    private void displayInterests(List<String> interestTags) {
        chipGroupUserInterests.removeAllViews();
        for (String tag : interestTags) {
            String trimmed = InterestTextUtils.stripLeadingIcon(tag);
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
        if (cachedMyPosts != null) {
            showActivePosts(cachedMyPosts);
            return;
        }
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
        cachedMyPosts = activePosts;
        if (activePosts.isEmpty()) {
            tvNoActivePosts.setVisibility(View.VISIBLE);
            rvActivePostsProfile.setVisibility(View.GONE);
        } else {
            tvNoActivePosts.setVisibility(View.GONE);
            rvActivePostsProfile.setVisibility(View.VISIBLE);
            // Only create adapter once — preserves scroll position across onResume
            if (myPostsAdapter == null) {
                rvActivePostsProfile.setLayoutManager(new LinearLayoutManager(this));
                boolean viewOther = getIntent().getBooleanExtra("view_other", false);
                if (viewOther) {
                    myPostsAdapter = new PostAdapter(this, activePosts, getMyInterestSet());
                } else {
                    myPostsAdapter = new PostAdapter(this, activePosts);
                }
                rvActivePostsProfile.setAdapter(myPostsAdapter);
            }
        }
    }

    /**
     * Load hoạt động đã tham gia: cho cả profile mình và profile người khác
     */
    private void loadMyActivities() {
        if (cachedMyActivities != null) {
            showMyActivities(cachedMyActivities);
            return;
        }
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
        cachedMyActivities = activities;
        View tvEmpty = findViewById(R.id.tvNoMyActivities);
        RecyclerView rv = findViewById(R.id.rvMyActivities);

        if (activities.isEmpty()) {
            if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            if (rv != null) rv.setVisibility(View.GONE);
        } else {
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            if (rv != null) {
                rv.setVisibility(View.VISIBLE);
                // Only create adapter once — preserves scroll position across onResume
                if (myActivitiesAdapter == null) {
                    rv.setLayoutManager(new LinearLayoutManager(this));
                    boolean viewOther = getIntent().getBooleanExtra("view_other", false);
                    if (viewOther) {
                        myActivitiesAdapter = new PostAdapter(this, activities, getMyInterestSet());
                    } else {
                        myActivitiesAdapter = new PostAdapter(this, activities);
                    }
                    rv.setAdapter(myActivitiesAdapter);
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
                String trimmed = InterestTextUtils.stripLeadingIcon(tag).toLowerCase();
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
            String trimmed = InterestTextUtils.stripLeadingIcon(tag);
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
                                    && interestSet.contains(InterestTextUtils.stripLeadingIcon(pr.getInterestTag()).toLowerCase())) {
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

    private void applyNavBadge() {
        if (footerNavigationProfile == null) return;
        int count = BadgeManager.getCount();
        if (count > 0) {
            com.google.android.material.badge.BadgeDrawable badge =
                    footerNavigationProfile.getOrCreateBadge(R.id.nav_notifications);
            badge.setVisible(true);
            badge.setMaxCharacterCount(3);
            badge.setNumber(count);
        } else {
            footerNavigationProfile.removeBadge(R.id.nav_notifications);
        }
    }

    private void bindSocialState() {
        boolean viewOther = getIntent().getBooleanExtra("view_other", false);
        long myUserId = RetrofitClient.getUserId(this);
        boolean isOwnProfile = !viewOther && (viewedUserId == -1 || viewedUserId == myUserId);

        if (blockProfileMode || isBlockedBetweenUsers) {
            showBlockedProfileState();
            return;
        }

        if (isOwnProfile) {
            // === Hồ sơ của mình ===
            ivBackUserProfile.setVisibility(View.GONE);
            ivMenuProfile.setVisibility(View.VISIBLE);
            tvFriendCount.setVisibility(View.VISIBLE);
            layoutSocialButtons.setVisibility(View.GONE);
            layoutRateReport.setVisibility(View.GONE);
            btnViewArchive.setVisibility(View.VISIBLE);
            footerNavigationProfile.setVisibility(View.VISIBLE);
            footerNavigationProfile.setSelectedItemId(R.id.nav_profile);
            applyNavBadge();

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
        if (viewedUserId > 0) {
            checkAndSetupRateButton(viewedUserId);
        } else {
            btnRateUser.setEnabled(false);
        }

        // 3-dots menu ở header cho profile người khác — dùng iOS-style BottomSheet
        ivMenuProfile.setImageResource(R.drawable.ic_more);
        ivMenuProfile.setOnClickListener(v -> showOtherUserActionSheet());

        ivUserProfileAvatar.setClickable(true);
        ivUserProfileAvatar.setOnClickListener(v -> {
            android.app.Dialog dialog = new android.app.Dialog(
                    UserProfileActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            ImageView imgFull = new ImageView(UserProfileActivity.this);
            imgFull.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imgFull.setBackgroundColor(Color.BLACK);
            imgFull.setOnClickListener(v2 -> dialog.dismiss());
            dialog.setContentView(imgFull);
            dialog.show();
            if (cachedOtherAvatarUrl != null && !cachedOtherAvatarUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(UserProfileActivity.this)
                        .load(cachedOtherAvatarUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .into(imgFull);
            } else {
                imgFull.setImageResource(R.drawable.ic_user_placeholder);
            }
        });

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

    private void showBlockedProfileState() {
        blockProfileMode = true;
        isBlockedBetweenUsers = true;

        ivBackUserProfile.setVisibility(View.VISIBLE);
        ivMenuProfile.setVisibility(View.GONE);
        footerNavigationProfile.setVisibility(View.GONE);
        tvFriendCount.setVisibility(View.GONE);
        btnViewArchive.setVisibility(View.GONE);
        layoutRateReport.setVisibility(View.GONE);
        cardCreatePostProfile.setVisibility(View.GONE);

        ivUserProfileAvatar.setImageResource(R.drawable.ic_user_placeholder);
        if (hasBlockedMe && !isBlockedByMe) {
            // Người này đã chặn tôi — hiển thị như không tồn tại
            tvUserProfileName.setText("Người dùng không tồn tại");
            tvUserBio.setText("Tài khoản này không khả dụng.");
        } else {
            // Tôi đã chặn họ — hiển thị nội dung bị ẩn
            tvUserProfileName.setText("Nội dung này không hiển thị");
            tvUserBio.setText("Bạn đã chặn người dùng này. Bỏ chặn để xem nội dung của họ.");
        }
        tvUserBio.setVisibility(View.VISIBLE);
        tvUserBirthday.setVisibility(View.GONE);
        tvUserGender.setVisibility(View.GONE);
        if (tvUserProvince != null) tvUserProvince.setVisibility(View.GONE);
        tvUserReputation.setText("0");

        tvInterestsTitle.setVisibility(View.GONE);
        chipGroupUserInterests.setVisibility(View.GONE);
        tabLayoutProfile.setVisibility(View.GONE);
        containerMyPosts.setVisibility(View.GONE);
        containerMyActivities.setVisibility(View.GONE);
        tvNoActivePosts.setVisibility(View.GONE);
        tvReviewsTitle.setVisibility(View.GONE);
        rvUserReviews.setVisibility(View.GONE);
        tvRelatedPostsTitle.setVisibility(View.GONE);
        tvNoRelatedPosts.setVisibility(View.GONE);
        rvRelatedPosts.setVisibility(View.GONE);

        if (isBlockedByMe && viewedUserId > 0) {
            layoutSocialButtons.setVisibility(View.VISIBLE);
            btnAddFriend.setVisibility(View.VISIBLE);
            btnAddFriend.setText("Bỏ chặn");
            btnAddFriend.setEnabled(true);
            btnAddFriend.setAlpha(1.0f);
            btnAddFriend.setOnClickListener(v -> unblockUserFromBlockedProfile());
            btnMessage.setVisibility(View.GONE);
        } else {
            layoutSocialButtons.setVisibility(View.GONE);
        }
    }

    private void unblockUserFromBlockedProfile() {
        if (viewedUserId <= 0) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        friendApiService.unblockUser(viewedUserId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(UserProfileActivity.this, "Đã bỏ chặn", Toast.LENGTH_SHORT).show();
                    blockProfileMode = false;
                    isBlockedByMe = false;
                    hasBlockedMe = false;
                    isBlockedBetweenUsers = false;
                    getIntent().removeExtra("blocked_profile");
                    getIntent().removeExtra("is_blocked_by_me");
                    getIntent().removeExtra("has_blocked_me");
                    recreate();
                } else {
                    Toast.makeText(UserProfileActivity.this, "Không thể bỏ chặn", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
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
                tvFriendCount.setOnClickListener(v -> openFriendsList());
            }

            @Override
            public void onFailure(Call<ApiResponse<Integer>> call, Throwable t) {
                tvFriendCount.setText("👥 Bạn bè: 0");
                tvFriendCount.setOnClickListener(v -> openFriendsList());
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
        btnMessage.setVisibility(View.VISIBLE);
        btnMessage.setOnClickListener(v -> openDirectMessageFromProfile());

        switch (status) {
            case "BLOCKED":
                btnAddFriend.setText("Đã chặn");
                btnAddFriend.setEnabled(false);
                btnAddFriend.setAlpha(0.5f);
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
                btnAddFriend.setOnClickListener(v -> showFriendOptionsMenu());
                break;

            case "PENDING_SENT":
                btnAddFriend.setText("Đã gửi lời mời");
                btnAddFriend.setEnabled(true);
                btnAddFriend.setAlpha(0.8f);
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
                btnAddFriend.setOnClickListener(v -> showFriendResponseDialog());
                break;

            default: // NONE
                btnAddFriend.setText("+ Thêm bạn bè");
                btnAddFriend.setEnabled(true);
                btnAddFriend.setAlpha(1.0f);
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

    private void openDirectMessageFromProfile() {
        if (isBlockedBetweenUsers || blockProfileMode) {
            Toast.makeText(this, "Bạn không thể nhắn tin cho người này.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (viewedUserId <= 0) {
            Toast.makeText(this, "Không thể nhắn tin", Toast.LENGTH_SHORT).show();
            return;
        }
        DirectMessageHelper.openDirectMessage(this, viewedUserId, username);
    }

    private void showOtherUserActionSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = buildIosRoot();
        makeSheetTransparent(root);

        LinearLayout group1 = buildIosGroup();
        TextView tvHeader = new TextView(this);
        tvHeader.setText(username != null ? username : "Người dùng");
        tvHeader.setTextSize(13);
        tvHeader.setTextColor(0xFF8E8E93);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
        group1.addView(tvHeader, matchW());
        addIosSep(group1);
        addIosRow(group1, "Chặn người dùng", 0xFFFF3B30, v -> {
            sheet.dismiss();
            showBlockUserConfirmDialog();
        });
        addIosSep(group1);
        addIosRow(group1, "Báo cáo người dùng", 0xFF1C1C1E, v -> {
            sheet.dismiss();
            UserReportBottomSheet.show(this, viewedUserId, username);
        });
        root.addView(group1, matchW());
        addGroupGap(root);

        LinearLayout group2 = buildIosGroup();
        addIosRow(group2, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group2, matchW());

        sheet.setContentView(root);
        sheet.show();
    }

    private void openFriendsList() {
        Intent intent = new Intent(this, FriendsListActivity.class);
        startActivity(intent);
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

    // Gọi canReview API để quyết định trạng thái nút "Đánh giá"
    private void checkAndSetupRateButton(long targetUserId) {
        reviewApiService.canReview(targetUserId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    Map<String, Object> result = response.body().getResult();
                    boolean canReview = Boolean.TRUE.equals(result.get("canReview"));
                    Object existingIdObj = result.get("existingReviewId");

                    if (canReview) {
                        // Có thể viết đánh giá mới
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> activities =
                                (List<Map<String, Object>>) result.get("commonActivities");
                        btnRateUser.setText("Viết đánh giá");
                        btnRateUser.setEnabled(true);
                        btnRateUser.setAlpha(1f);
                        btnRateUser.setOnClickListener(v -> {
                            if (activities != null && !activities.isEmpty()) {
                                showWriteReviewDialog(activities);
                            } else {
                                Toast.makeText(UserProfileActivity.this,
                                        "Không tìm thấy hoạt động chung đã kết thúc", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (existingIdObj != null) {
                        // Đã đánh giá rồi — chuyển sang chỉnh sửa
                        @SuppressWarnings("unchecked")
                        Map<String, Object> existingReview =
                                (Map<String, Object>) result.get("existingReview");
                        btnRateUser.setText("Chỉnh sửa đánh giá");
                        btnRateUser.setEnabled(true);
                        btnRateUser.setAlpha(1f);
                        btnRateUser.setOnClickListener(v -> {
                            if (existingReview != null) {
                                showEditReviewDialog(existingReview);
                            }
                        });
                    } else {
                        // Không đủ điều kiện đánh giá
                        String reason = result.get("reason") != null ? result.get("reason").toString() : "";
                        btnRateUser.setText("Đánh giá");
                        btnRateUser.setEnabled(false);
                        btnRateUser.setAlpha(0.45f);
                        if (!reason.isEmpty()) {
                            btnRateUser.setOnClickListener(v ->
                                    Toast.makeText(UserProfileActivity.this, reason, Toast.LENGTH_LONG).show());
                            btnRateUser.setEnabled(true);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                btnRateUser.setEnabled(false);
                btnRateUser.setAlpha(0.45f);
            }
        });
    }

    // Dialog viết đánh giá mới — iOS style
    private void showWriteReviewDialog(List<Map<String, Object>> activities) {
        // Build display names và giữ postId tương ứng
        List<String> displayNames = new ArrayList<>();
        List<Long> postIds = new ArrayList<>();
        for (Map<String, Object> item : activities) {
            String dateDisplay = item.get("activityDateDisplay") != null
                    ? item.get("activityDateDisplay").toString() : "";
            String tag = item.get("interestTag") != null ? item.get("interestTag").toString() : "";
            String display = !dateDisplay.isEmpty() ? dateDisplay
                    : (!tag.isEmpty() ? tag : "Hoạt động chung");
            displayNames.add(display);
            long pid = item.get("postId") != null ? ((Number) item.get("postId")).longValue() : 0L;
            postIds.add(pid);
        }

        final int[] selectedActivityIndex = {0};
        final TextView[] activityViews = new TextView[displayNames.size()];
        final RatingBar[] ratingBarHolder = new RatingBar[1];
        final EditText[] etHolder = new EditText[1];

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = buildIosRoot();
        makeSheetTransparent(root);

        // Group 1 — Header + chọn hoạt động
        LinearLayout group1 = buildIosGroup();

        TextView tvHeader = new TextView(this);
        tvHeader.setText("Đánh giá " + username);
        tvHeader.setTextSize(13);
        tvHeader.setTextColor(0xFF8E8E93);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
        group1.addView(tvHeader, matchW());

        if (displayNames.size() == 1) {
            addIosSep(group1);
            TextView tvSingle = new TextView(this);
            tvSingle.setText(displayNames.get(0));
            tvSingle.setTextSize(15);
            tvSingle.setTextColor(0xFF8E8E93);
            tvSingle.setGravity(Gravity.CENTER);
            tvSingle.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
            group1.addView(tvSingle, matchW());
        } else {
            for (int i = 0; i < displayNames.size(); i++) {
                final int idx = i;
                addIosSep(group1);
                TextView tvAct = new TextView(this);
                tvAct.setText(displayNames.get(i));
                tvAct.setTextSize(17);
                tvAct.setTextColor(i == 0 ? 0xFF007AFF : 0xFF1C1C1E);
                tvAct.setGravity(Gravity.CENTER);
                tvAct.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
                tvAct.setClickable(true);
                tvAct.setFocusable(true);
                android.util.TypedValue ripple = new android.util.TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
                tvAct.setBackgroundResource(ripple.resourceId);
                tvAct.setOnClickListener(v -> {
                    if (activityViews[selectedActivityIndex[0]] != null)
                        activityViews[selectedActivityIndex[0]].setTextColor(0xFF1C1C1E);
                    selectedActivityIndex[0] = idx;
                    tvAct.setTextColor(0xFF007AFF);
                });
                activityViews[i] = tvAct;
                group1.addView(tvAct, matchW());
            }
        }

        root.addView(group1, matchW());
        addGroupGap(root);

        // Group 2 — RatingBar
        LinearLayout group2 = buildIosGroup();
        TextView tvRatingLabel = new TextView(this);
        tvRatingLabel.setText("Số sao đánh giá");
        tvRatingLabel.setTextSize(17);
        tvRatingLabel.setTextColor(0xFF1C1C1E);
        tvRatingLabel.setGravity(Gravity.CENTER);
        tvRatingLabel.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
        group2.addView(tvRatingLabel, matchW());

        addIosSep(group2);

        LinearLayout ratingContainer = new LinearLayout(this);
        ratingContainer.setGravity(Gravity.CENTER);
        ratingContainer.setPadding(dpPx(20), dpPx(14), dpPx(20), dpPx(14));
        RatingBar ratingBar = new RatingBar(this, null, android.R.attr.ratingBarStyle);
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(0f);
        ratingContainer.addView(ratingBar);
        ratingBarHolder[0] = ratingBar;
        group2.addView(ratingContainer, matchW());
        root.addView(group2, matchW());
        addGroupGap(root);

        // Group 3 — EditText nhận xét
        LinearLayout group3 = buildIosGroup();
        EditText etComment = new EditText(this);
        etComment.setHint("Nhận xét (không bắt buộc)");
        etComment.setTextSize(17);
        etComment.setBackground(null);
        etComment.setMinLines(2);
        etComment.setMaxLines(5);
        etComment.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
        etHolder[0] = etComment;
        group3.addView(etComment, matchW());
        root.addView(group3, matchW());
        addGroupGap(root);

        // Group 4 — Gửi đánh giá
        LinearLayout group4 = buildIosGroup();
        addIosRow(group4, "Gửi đánh giá", 0xFF007AFF, v -> {
            if (ratingBarHolder[0].getRating() == 0) {
                Toast.makeText(this, "Vui lòng chọn số sao", Toast.LENGTH_SHORT).show();
                return;
            }
            long selectedPostId = postIds.get(selectedActivityIndex[0]);
            sheet.dismiss();
            submitNewReview((int) ratingBarHolder[0].getRating(),
                    etHolder[0].getText().toString().trim(), selectedPostId);
        });
        root.addView(group4, matchW());
        addGroupGap(root);

        // Group 5 — Huỷ
        LinearLayout group5 = buildIosGroup();
        addIosRow(group5, "Huỷ", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(group5, matchW());

        sheet.setContentView(root);
        sheet.show();
    }

    // Dialog chỉnh sửa đánh giá — bottom sheet nổi từ dưới, overlay tối
    private void showEditReviewDialog(Map<String, Object> existingReview) {
        long reviewId = existingReview.get("id") != null
                ? ((Number) existingReview.get("id")).longValue() : 0L;
        int currentRating = existingReview.get("rating") != null
                ? ((Number) existingReview.get("rating")).intValue() : 0;
        String currentComment = existingReview.get("comment") != null
                ? existingReview.get("comment").toString() : "";
        String activityDisplay = existingReview.get("activityDateDisplay") != null
                ? existingReview.get("activityDateDisplay").toString() : "";

        final RatingBar[] ratingBarHolder = new RatingBar[1];
        final EditText[] etHolder = new EditText[1];

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        // ── Root container — nền trắng, bo góc trên 24dp ──
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable sheetBg = new android.graphics.drawable.GradientDrawable();
        sheetBg.setColor(0xFFFFFFFF);
        float r = dpPx(24);
        sheetBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(sheetBg);

        // ── Handle bar (drag indicator) ──
        LinearLayout handleWrap = new LinearLayout(this);
        handleWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        handleWrap.setPadding(0, dpPx(12), 0, dpPx(4));
        View handle = new View(this);
        android.graphics.drawable.GradientDrawable handleBg = new android.graphics.drawable.GradientDrawable();
        handleBg.setColor(0xFFD1D1D6);
        handleBg.setCornerRadius(dpPx(3));
        handle.setBackground(handleBg);
        handleWrap.addView(handle, new LinearLayout.LayoutParams(dpPx(36), dpPx(4)));
        root.addView(handleWrap, matchW());

        // ── Content padding wrapper ──
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpPx(20), dpPx(8), dpPx(20), dpPx(32));

        // ── Header row: title + nút đóng ──
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Chỉnh sửa đánh giá");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(0xFF1C1C1E);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        headerRow.addView(tvTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvClose = new TextView(this);
        tvClose.setText("✕");
        tvClose.setTextSize(18);
        tvClose.setTextColor(0xFFAEAEB2);
        tvClose.setPadding(dpPx(8), dpPx(4), 0, dpPx(4));
        tvClose.setClickable(true);
        tvClose.setFocusable(true);
        android.util.TypedValue rippleClose = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, rippleClose, true);
        tvClose.setBackgroundResource(rippleClose.resourceId);
        tvClose.setOnClickListener(v -> sheet.dismiss());
        headerRow.addView(tvClose);

        content.addView(headerRow, matchW());

        // ── Activity chip ──
        if (!activityDisplay.isEmpty()) {
            TextView tvAct = new TextView(this);
            tvAct.setText("📌 " + activityDisplay);
            tvAct.setTextSize(12);
            tvAct.setTextColor(0xFF6D6D72);
            tvAct.setPadding(dpPx(10), dpPx(5), dpPx(10), dpPx(5));
            android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
            chipBg.setColor(0xFFF2F2F7);
            chipBg.setCornerRadius(dpPx(20));
            tvAct.setBackground(chipBg);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipParams.topMargin = dpPx(8);
            content.addView(tvAct, chipParams);
        }

        // ── Divider ──
        content.addView(makeDivider(dpPx(16)));

        // ── Rating ──
        TextView tvRatingLabel = new TextView(this);
        tvRatingLabel.setText("Đánh giá của bạn");
        tvRatingLabel.setTextSize(14);
        tvRatingLabel.setTextColor(0xFF3A3A3C);
        tvRatingLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dpPx(16);
        content.addView(tvRatingLabel, rlp);

        LinearLayout ratingRow = new LinearLayout(this);
        ratingRow.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams rrp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rrp.topMargin = dpPx(8);
        RatingBar ratingBar = new RatingBar(this, null, android.R.attr.ratingBarStyle);
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(currentRating);
        ratingBarHolder[0] = ratingBar;
        ratingRow.addView(ratingBar);
        content.addView(ratingRow, rrp);

        // ── Divider ──
        content.addView(makeDivider(dpPx(16)));

        // ── Comment ──
        TextView tvCommentLabel = new TextView(this);
        tvCommentLabel.setText("Nhận xét");
        tvCommentLabel.setTextSize(14);
        tvCommentLabel.setTextColor(0xFF3A3A3C);
        tvCommentLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dpPx(16);
        content.addView(tvCommentLabel, clp);

        EditText etComment = new EditText(this);
        etComment.setHint("Chia sẻ trải nghiệm của bạn...");
        etComment.setText(currentComment);
        etComment.setTextSize(15);
        etComment.setTextColor(0xFF1C1C1E);
        etComment.setHintTextColor(0xFFAEAEB2);
        etComment.setMinLines(3);
        etComment.setMaxLines(6);
        etComment.setGravity(Gravity.TOP | Gravity.START);
        etComment.setPadding(dpPx(14), dpPx(12), dpPx(14), dpPx(12));
        android.graphics.drawable.GradientDrawable etBg = new android.graphics.drawable.GradientDrawable();
        etBg.setColor(0xFFF2F2F7);
        etBg.setCornerRadius(dpPx(10));
        etComment.setBackground(etBg);
        LinearLayout.LayoutParams etp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etp.topMargin = dpPx(8);
        etHolder[0] = etComment;
        content.addView(etComment, etp);

        // ── CTA chính: Lưu chỉnh sửa ──
        MaterialButton btnSave = new MaterialButton(this);
        btnSave.setText("Lưu chỉnh sửa");
        btnSave.setAllCaps(false);
        btnSave.setCornerRadius(dpPx(12));
        btnSave.setTextSize(16);
        btnSave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF007AFF));
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(48));
        sbp.topMargin = dpPx(20);
        btnSave.setOnClickListener(v -> {
            if (ratingBarHolder[0].getRating() == 0) {
                Toast.makeText(this, "Vui lòng chọn số sao", Toast.LENGTH_SHORT).show();
                return;
            }
            sheet.dismiss();
            updateExistingReview(reviewId, (int) ratingBarHolder[0].getRating(),
                    etHolder[0].getText().toString().trim());
        });
        content.addView(btnSave, sbp);

        // ── Row phụ: Xóa | Hủy ──
        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setOrientation(LinearLayout.HORIZONTAL);
        secondaryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srp.topMargin = dpPx(4);

        TextView tvDelete = makeTextAction("Xóa đánh giá", 0xFFFF3B30);
        tvDelete.setOnClickListener(v -> {
            sheet.dismiss();
            new AlertDialog.Builder(this)
                    .setTitle("Xóa đánh giá")
                    .setMessage("Bạn có chắc muốn xóa đánh giá này?")
                    .setPositiveButton("Xóa", (d, w) -> deleteReview(reviewId))
                    .setNegativeButton("Huỷ", null)
                    .show();
        });
        secondaryRow.addView(tvDelete, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        View vDivider = new View(this);
        vDivider.setBackgroundColor(0xFFE5E5EA);
        LinearLayout.LayoutParams vdp = new LinearLayout.LayoutParams(1, dpPx(18));
        vdp.gravity = Gravity.CENTER_VERTICAL;
        vDivider.setLayoutParams(vdp);
        secondaryRow.addView(vDivider);

        TextView tvCancel = makeTextAction("Hủy", 0xFF8E8E93);
        tvCancel.setOnClickListener(v -> sheet.dismiss());
        secondaryRow.addView(tvCancel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        content.addView(secondaryRow, srp);
        root.addView(content, matchW());

        sheet.setContentView(root);

        // Xóa nền Material của design_bottom_sheet để lộ nền trắng bo góc, giữ overlay tối
        sheet.setOnShowListener(d -> {
            android.view.View bottomSheet = sheet.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(null);
                com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        sheet.show();
    }

    private View makeDivider(int topMargin) {
        View div = new View(this);
        div.setBackgroundColor(0xFFE5E5EA);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        p.topMargin = topMargin;
        div.setLayoutParams(p);
        return div;
    }

    private TextView makeTextAction(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpPx(8), dpPx(14), dpPx(8), dpPx(14));
        tv.setClickable(true);
        tv.setFocusable(true);
        android.util.TypedValue ripple = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        tv.setBackgroundResource(ripple.resourceId);
        return tv;
    }

    // Bottom sheet chi tiết đánh giá — iOS style
    private void showReviewDetailSheet(UserReview review) {
        long currentUserId = RetrofitClient.getUserId(this);
        boolean isOwner = currentUserId > 0 && review.getReviewerId() == currentUserId;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = buildIosRoot();
        makeSheetTransparent(root);

        // Group 1 — nội dung chi tiết
        LinearLayout group1 = buildIosGroup();

        // Header: avatar + tên người viết đánh giá (clickable → profile)
        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        profileRow.setPadding(dpPx(20), dpPx(14), dpPx(20), dpPx(14));
        profileRow.setClickable(true);
        profileRow.setFocusable(true);
        android.util.TypedValue profileRipple = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, profileRipple, true);
        profileRow.setBackgroundResource(profileRipple.resourceId);
        profileRow.setOnClickListener(v -> {
            sheet.dismiss();
            if (review.getReviewerId() > 0) {
                Intent profileIntent = new Intent(this, UserProfileActivity.class);
                profileIntent.putExtra("user_id", review.getReviewerId());
                profileIntent.putExtra("view_other", true);
                startActivity(profileIntent);
            }
        });

        android.widget.ImageView ivDetailAvatar = new android.widget.ImageView(this);
        int avSize = dpPx(40);
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(avSize, avSize);
        ivDetailAvatar.setLayoutParams(avp);
        ivDetailAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        String avUrl = review.getReviewerAvatarUrl();
        if (avUrl != null && !avUrl.isEmpty()) {
            if (!avUrl.startsWith("http")) {
                String base = RetrofitClient.getBaseUrl();
                avUrl = avUrl.startsWith("/") ? base + avUrl.substring(1) : base + avUrl;
            }
            com.bumptech.glide.Glide.with(this)
                    .load(avUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(ivDetailAvatar);
        } else {
            ivDetailAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }
        profileRow.addView(ivDetailAvatar);

        TextView tvDetailName = new TextView(this);
        tvDetailName.setText(review.getReviewerName() != null ? review.getReviewerName() : "Ẩn danh");
        tvDetailName.setTextSize(15);
        tvDetailName.setTextColor(0xFF1C1C1E);
        tvDetailName.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.setMarginStart(dpPx(12));
        tvDetailName.setLayoutParams(nlp);
        profileRow.addView(tvDetailName);

        group1.addView(profileRow, matchW());

        // Rating — ★★★★☆ với sao vàng / xám
        if (review.getRating() != null && review.getRating() > 0) {
            addIosSep(group1);
            int starCount = review.getRating();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(i < starCount ? "★" : "☆");
            android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(sb.toString());
            ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFC107),
                    0, starCount, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (starCount < 5) {
                ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFFD1D1D6),
                        starCount, 5, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            TextView tvRating = new TextView(this);
            tvRating.setText(ssb);
            tvRating.setTextSize(22);
            tvRating.setGravity(Gravity.CENTER);
            tvRating.setPadding(dpPx(20), dpPx(14), dpPx(20),
                    (review.getReputationLabel() != null && !review.getReputationLabel().isEmpty()) ? dpPx(4) : dpPx(14));
            group1.addView(tvRating, matchW());

            if (review.getReputationLabel() != null && !review.getReputationLabel().isEmpty()) {
                TextView tvLabel = new TextView(this);
                tvLabel.setText(review.getReputationLabel());
                tvLabel.setTextSize(13);
                tvLabel.setTextColor(0xFF8E8E93);
                tvLabel.setGravity(Gravity.CENTER);
                tvLabel.setPadding(dpPx(20), 0, dpPx(20), dpPx(12));
                group1.addView(tvLabel, matchW());
            }
        }

        // Hoạt động chung — no emoji prefix
        String actDisplay = review.getActivityDateDisplay();
        if (actDisplay == null || actDisplay.isEmpty()) actDisplay = review.getActivityName();
        if (actDisplay != null && !actDisplay.isEmpty()) {
            addIosSep(group1);
            TextView tvAct = new TextView(this);
            tvAct.setText(actDisplay);
            tvAct.setTextSize(15);
            tvAct.setTextColor(0xFF8E8E93);
            tvAct.setGravity(Gravity.CENTER);
            tvAct.setPadding(dpPx(20), dpPx(14), dpPx(20), dpPx(14));
            group1.addView(tvAct, matchW());
        }

        // Comment
        if (review.getComment() != null && !review.getComment().isEmpty()) {
            addIosSep(group1);
            TextView tvComment = new TextView(this);
            tvComment.setText(review.getComment());
            tvComment.setTextSize(17);
            tvComment.setTextColor(0xFF1C1C1E);
            tvComment.setPadding(dpPx(20), dpPx(15), dpPx(20), dpPx(15));
            group1.addView(tvComment, matchW());
        }

        // Ngày tạo / chỉnh sửa
        String dateText = review.getCreatedAt() != null ? review.getCreatedAt() : "";
        if (review.isEdited() && review.getUpdatedAt() != null) {
            dateText += " · Đã chỉnh sửa " + review.getUpdatedAt();
        }
        if (!dateText.isEmpty()) {
            addIosSep(group1);
            TextView tvDate = new TextView(this);
            tvDate.setText(dateText);
            tvDate.setTextSize(13);
            tvDate.setTextColor(0xFF8E8E93);
            tvDate.setGravity(Gravity.CENTER);
            tvDate.setPadding(dpPx(20), dpPx(10), dpPx(20), dpPx(10));
            group1.addView(tvDate, matchW());
        }

        root.addView(group1, matchW());
        addGroupGap(root);

        // Nếu là người viết — hiện Chỉnh sửa và Xóa
        if (isOwner) {
            LinearLayout groupEdit = buildIosGroup();
            addIosRow(groupEdit, "Chỉnh sửa đánh giá", 0xFF007AFF, v -> {
                sheet.dismiss();
                Map<String, Object> reviewMap = new HashMap<>();
                reviewMap.put("id", review.getId());
                reviewMap.put("rating", review.getRating());
                reviewMap.put("comment", review.getComment());
                reviewMap.put("activityDateDisplay", review.getActivityDateDisplay());
                showEditReviewDialog(reviewMap);
            });
            root.addView(groupEdit, matchW());
            addGroupGap(root);

            LinearLayout groupDelete = buildIosGroup();
            addIosRow(groupDelete, "Xóa đánh giá", 0xFFFF3B30, v -> {
                sheet.dismiss();
                new AlertDialog.Builder(this)
                        .setTitle("Xóa đánh giá")
                        .setMessage("Bạn có chắc muốn xóa đánh giá này?")
                        .setPositiveButton("Xóa", (d, w) -> deleteReview(review.getId()))
                        .setNegativeButton("Huỷ", null)
                        .show();
            });
            root.addView(groupDelete, matchW());
            addGroupGap(root);
        }

        // Đóng
        LinearLayout groupClose = buildIosGroup();
        addIosRow(groupClose, "Đóng", 0xFF1C1C1E, v -> sheet.dismiss());
        root.addView(groupClose, matchW());

        sheet.setContentView(root);
        sheet.show();
    }

    private void submitNewReview(int stars, String comment, long postId) {
        long reviewedUserId = getIntent().getLongExtra("user_id", -1);
        if (reviewedUserId <= 0) {
            Toast.makeText(this, "Không thể gửi đánh giá", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = {"Cần cải thiện", "Trung bình", "Tích cực", "Đáng tin cậy", "Xuất sắc"};
        String reputationLabel = labels[Math.min(stars - 1, labels.length - 1)];

        Map<String, Object> body = new HashMap<>();
        body.put("reviewedUserId", reviewedUserId);
        body.put("postId", postId);
        body.put("rating", stars);
        body.put("reputationLabel", reputationLabel);
        body.put("comment", comment.isEmpty() ? "" : comment);

        reviewApiService.createReview(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(UserProfileActivity.this,
                            "Đã gửi đánh giá " + stars + " sao cho " + username,
                            Toast.LENGTH_SHORT).show();
                    loadReviewsFromBackend();
                    loadOtherUserProfile(reviewedUserId);
                    checkAndSetupRateButton(reviewedUserId);
                } else {
                    String errorMsg = "Không thể gửi đánh giá";
                    try {
                        if (response.errorBody() != null) {
                            String errorJson = response.errorBody().string();
                            org.json.JSONObject json = new org.json.JSONObject(errorJson);
                            if (json.has("message")) errorMsg = json.getString("message");
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(UserProfileActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateExistingReview(long reviewId, int stars, String comment) {
        String[] labels = {"Cần cải thiện", "Trung bình", "Tích cực", "Đáng tin cậy", "Xuất sắc"};
        String reputationLabel = labels[Math.min(stars - 1, labels.length - 1)];

        Map<String, Object> body = new HashMap<>();
        body.put("rating", stars);
        body.put("reputationLabel", reputationLabel);
        body.put("comment", comment);

        reviewApiService.updateReview(reviewId, body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(UserProfileActivity.this, "Đã cập nhật đánh giá", Toast.LENGTH_SHORT).show();
                    loadReviewsFromBackend();
                    long targetId = getIntent().getLongExtra("user_id", -1);
                    if (targetId > 0) {
                        loadOtherUserProfile(targetId);
                        checkAndSetupRateButton(targetId);
                    }
                } else {
                    Toast.makeText(UserProfileActivity.this, "Không thể cập nhật đánh giá", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(UserProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteReview(long reviewId) {
        reviewApiService.deleteReview(reviewId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(UserProfileActivity.this, "Đã xóa đánh giá", Toast.LENGTH_SHORT).show();
                    loadReviewsFromBackend();
                    long targetId = getIntent().getLongExtra("user_id", -1);
                    if (targetId > 0) {
                        loadOtherUserProfile(targetId);
                        checkAndSetupRateButton(targetId);
                    }
                } else {
                    Toast.makeText(UserProfileActivity.this, "Không thể xóa đánh giá", Toast.LENGTH_SHORT).show();
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
        boolean isViewOther = getIntent().getBooleanExtra("view_other", false);
        if (targetUserId <= 0 && !isViewOther) {
            // Chỉ fallback sang userId của bản thân khi đang xem profile của chính mình
            android.content.SharedPreferences prefs = getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
            targetUserId = prefs.getLong("user_id", -1);
        }
        if (targetUserId <= 0) {
            rvUserReviews.setAdapter(new UserReviewAdapter(new ArrayList<>(), 0, null));
            return;
        }

        long currentUserId = RetrofitClient.getUserId(this);

        reviewApiService.getReviews(targetUserId).enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    List<Map<String, Object>> reviewMaps = response.body().getResult();
                    List<UserReview> reviews = new ArrayList<>();
                    for (Map<String, Object> map : reviewMaps) {
                        UserReview r = new UserReview();
                        if (map.get("id") != null) r.setId(((Number) map.get("id")).longValue());
                        if (map.get("reviewerId") != null) r.setReviewerId(((Number) map.get("reviewerId")).longValue());
                        if (map.get("reviewedUserId") != null) r.setReviewedUserId(((Number) map.get("reviewedUserId")).longValue());
                        if (map.get("postId") != null) r.setPostId(((Number) map.get("postId")).longValue());
                        if (map.get("rating") != null) r.setRating(((Number) map.get("rating")).intValue());
                        r.setReviewerName(map.get("reviewerName") != null ? map.get("reviewerName").toString() : "Ẩn danh");
                        r.setReviewerAvatarUrl(map.get("reviewerAvatarUrl") != null ? map.get("reviewerAvatarUrl").toString() : null);
                        r.setReputationLabel(map.get("reputationLabel") != null ? map.get("reputationLabel").toString() : "");
                        r.setComment(map.get("comment") != null ? map.get("comment").toString() : "");
                        r.setActivityName(map.get("activityName") != null ? map.get("activityName").toString() : "");
                        r.setInterestTag(map.get("interestTag") != null ? map.get("interestTag").toString() : "");
                        r.setActivityDateDisplay(map.get("activityDateDisplay") != null ? map.get("activityDateDisplay").toString() : "");
                        r.setCreatedAt(map.get("createdAt") != null ? map.get("createdAt").toString() : "");
                        r.setUpdatedAt(map.get("updatedAt") != null ? map.get("updatedAt").toString() : null);
                        r.setEdited(Boolean.TRUE.equals(map.get("isEdited")));
                        reviews.add(r);
                    }
                    // Chỉ người xem profile của chính mình mới thấy nút báo cáo
                    UserReviewAdapter.OnReviewReportListener reportListener =
                            isViewOther ? null : review ->
                                    ReviewReportBottomSheet.show(UserProfileActivity.this,
                                            getSupportFragmentManager(), review);
                    rvUserReviews.setAdapter(new UserReviewAdapter(reviews, currentUserId,
                            review -> showReviewDetailSheet(review),
                            reportListener));
                    bindReputationSummaryCard(reviews.size());
                } else {
                    rvUserReviews.setAdapter(new UserReviewAdapter(new ArrayList<>(), 0, null));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                rvUserReviews.setAdapter(new UserReviewAdapter(new ArrayList<>(), 0, null));
            }
        });
    }

    private void bindReputationSummaryCard(int reviewCount) {
        if (cardReputationSummary == null) return;
        if (reviewCount == 0 && summaryReputationVal == 0) {
            cardReputationSummary.setVisibility(View.GONE);
            return;
        }
        cardReputationSummary.setVisibility(View.VISIBLE);
        if (tvSummaryReputation != null) {
            tvSummaryReputation.setText(summaryReputationVal + "/100");
            int color;
            if (summaryReputationVal >= 70) color = 0xFF4CAF50;
            else if (summaryReputationVal >= 40) color = 0xFFFF9800;
            else color = 0xFFF44336;
            tvSummaryReputation.setTextColor(color);
        }
        if (tvSummaryAvgRating != null) {
            if (summaryAvgRatingVal > 0) {
                tvSummaryAvgRating.setText(String.format("★ %.1f", summaryAvgRatingVal));
            } else {
                tvSummaryAvgRating.setText("★ --");
            }
        }
        if (tvSummaryReviewCount != null) {
            tvSummaryReviewCount.setText(String.valueOf(reviewCount));
        }
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

    private boolean isOtherReportReason(String reason) {
        return "Khác".equalsIgnoreCase(reason) || "Lý do khác".equalsIgnoreCase(reason);
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
        final LinearLayout[] otherReasonGroupRef = new LinearLayout[1];
        final EditText[] otherReasonInputRef = new EditText[1];

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
                boolean isOther = isOtherReportReason(reasons[index]);
                if (otherReasonGroupRef[0] != null) {
                    otherReasonGroupRef[0].setVisibility(isOther ? View.VISIBLE : View.GONE);
                }
                if (!isOther && otherReasonInputRef[0] != null) {
                    otherReasonInputRef[0].setText("");
                    otherReasonInputRef[0].setError(null);
                }
            });
            reasonViews[i] = tvReason;
            group1.addView(tvReason, matchW());
        }

        root.addView(group1, matchW());
        addGroupGap(root);

        // Group 2: field nhập lý do khác, chỉ hiện khi chọn "Lý do khác"
        LinearLayout group2 = buildIosGroup();
        group2.setVisibility(View.GONE);
        otherReasonGroupRef[0] = group2;
        TextView otherReasonHeader = new TextView(this);
        otherReasonHeader.setText("Nhập lý do khác");
        otherReasonHeader.setTextSize(13);
        otherReasonHeader.setTextColor(0xFF8E8E93);
        otherReasonHeader.setGravity(Gravity.CENTER);
        otherReasonHeader.setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12));
        group2.addView(otherReasonHeader, matchW());
        addIosSep(group2);
        EditText etDescription = new EditText(this);
        otherReasonInputRef[0] = etDescription;
        etDescription.setHint("Nhập lý do khác...");
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
            String selectedReason = reasons[selectedIndex[0]];
            String otherReason = etDescription.getText().toString().trim();
            if (isOtherReportReason(selectedReason) && otherReason.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập lý do khác", Toast.LENGTH_SHORT).show();
                etDescription.setError("Vui lòng nhập lý do khác");
                return;
            }
            sheet.dismiss();
            submitReportToBackend("USER", viewedUserId, selectedReason, otherReason);
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
                    if ("USER".equalsIgnoreCase(targetType)) {
                        showBlockSuggestionAfterReport(targetId);
                    } else {
                        Toast.makeText(UserProfileActivity.this,
                                "Đã gửi báo cáo. Cảm ơn bạn!", Toast.LENGTH_SHORT).show();
                    }
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

    private void showBlockSuggestionAfterReport(long targetUserId) {
        if (targetUserId <= 0) {
            Toast.makeText(this, "Đã gửi báo cáo. Cảm ơn bạn!", Toast.LENGTH_SHORT).show();
            return;
        }
        String safeName = username != null && !username.trim().isEmpty()
                ? username.trim()
                : "người dùng này";

        new AlertDialog.Builder(this)
                .setTitle("Đã gửi báo cáo")
                .setMessage("Cảm ơn bạn đã phản hồi. Bạn có muốn chặn " + safeName
                        + " để hạn chế tương tác và tin nhắn từ người này không?")
                .setNegativeButton("Để sau", null)
                .setPositiveButton("Chặn", (dialog, which) ->
                        friendApiService.blockUser(targetUserId).enqueue(new Callback<ApiResponse<Void>>() {
                            @Override
                            public void onResponse(Call<ApiResponse<Void>> call,
                                                   Response<ApiResponse<Void>> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(UserProfileActivity.this,
                                            "Đã chặn " + safeName, Toast.LENGTH_SHORT).show();
                                    if (targetUserId == viewedUserId) {
                                        setupFriendButton("BLOCKED");
                                    }
                                } else {
                                    Toast.makeText(UserProfileActivity.this,
                                            "Không thể chặn người dùng", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                Toast.makeText(UserProfileActivity.this,
                                        "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .show();
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
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(Color.BLACK);
            imageView.setOnClickListener(v2 -> dialog.dismiss());
            dialog.setContentView(imageView);
            dialog.show();
            String avatarUrl = RetrofitClient.getAvatarUrl(this);
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .into(imageView);
            } else {
                imageView.setImageResource(R.drawable.ic_user_placeholder);
            }
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
            if (data != null) {
                RetrofitClient.saveUserProvince(
                        this,
                        data.getStringExtra("provinceId"),
                        data.getStringExtra("provinceName")
                );
            }
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
                        final String avatarUrl = response.body().getResult();
                        // Persist relative path so URL stays valid across IP changes
                        RetrofitClient.saveAvatarUrl(UserProfileActivity.this, avatarUrl);
                        // Push new avatarUrl to backend profile
                        updateProfileAvatarOnBackend(avatarUrl);
                        // Wipe Glide cache so every screen shows fresh avatar on next load
                        com.bumptech.glide.Glide.get(UserProfileActivity.this).clearMemory();
                        new Thread(() -> com.bumptech.glide.Glide.get(UserProfileActivity.this).clearDiskCache()).start();
                        // Reload own avatar ImageView immediately, bypassing old cache
                        final String finalUrl = resolveAvatarUrl(avatarUrl);
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
        String activityStartIso = data.getStringExtra("post_activity_start_iso");
        String activityEndIso = data.getStringExtra("post_activity_end_iso");
        body.put("startTime", activityStartIso != null ? activityStartIso : isoFormat.format(new java.util.Date()));
        body.put("endTime", isoFormat.format(new java.util.Date(endTimeMillis)));
        if (activityEndIso != null) {
            body.put("activityEndTime", activityEndIso);
        }

        RetrofitClient.loadToken(this);
        com.example.weconnect.api.PostApiService postApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.PostApiService.class);
        postApi.updatePost(postId, body).enqueue(new Callback<ApiResponse<com.example.weconnect.models.PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<com.example.weconnect.models.PostResponse>> call,
                                   Response<ApiResponse<com.example.weconnect.models.PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(UserProfileActivity.this, "Đã cập nhật bài viết!", Toast.LENGTH_SHORT).show();
                    cachedMyPosts = null;
                    myPostsAdapter = null;
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
