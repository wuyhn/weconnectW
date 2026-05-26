package com.example.weconnect.activities;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.util.BadgeManager;
import com.example.weconnect.adapters.PostAdapter;
import com.google.gson.Gson;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.websocket.WebSocketManager;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.PostResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ImageView ivAdd, ivSearch;
    private FrameLayout btnHome, btnMessages, btnNotifications, btnProfile;
    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private List<Post> postList;
    private View statusHeader;
    private FakePostRepository postRepository;
    private PostApiService postApiService;
    private ActivityResultLauncher<Intent> createPostLauncher;
    private android.widget.TextView tvNotifBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        postRepository = FakePostRepository.getInstance();

        // Load JWT token đã lưu
        RetrofitClient.loadToken(this);
        postApiService = RetrofitClient.getClient().create(PostApiService.class);

        // Khởi tạo WebSocket connection
        String token = RetrofitClient.getAuthToken();
        if (token != null && !WebSocketManager.getInstance().isConnected()) {
            WebSocketManager.getInstance().connect(RetrofitClient.getBaseUrl(), token);
        }

        // Sync tên user thật với FakeRepositories (để profile detection hoạt động)
        String realName = RetrofitClient.getUserName(this);
        if (realName != null && !realName.isEmpty()) {
            com.example.weconnect.data.FakeSocialRepository.getInstance().setCurrentUsername(realName);
            postRepository.setCurrentUsername(realName);
        }

        setupActivityResultLauncher();
        initViews();
        setupClickListeners();
        setupRecyclerView();
        loadUnreadNotificationCount();
        createNotificationChannel();
        requestNotificationPermission();
        fetchAndRegisterFcmToken();
    }

    // Cờ kiểm soát: true = lần đầu vào màn hình, false = quay lại từ màn hình khác
    private boolean isFirstResume = true;

    @Override
    protected void onResume() {
        super.onResume();

        // Chỉ load token 1 lần (không reset retrofit)
        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();

        // Reconnect WebSocket nếu mất kết nối
        if (token != null && !WebSocketManager.getInstance().isConnected()) {
            WebSocketManager.getInstance().connect(RetrofitClient.getBaseUrl(), token);
        }

        if (isFirstResume) {
            // Lần đầu vào: load đầy đủ tất cả dữ liệu nền
            isFirstResume = false;
            syncInterestsFromBackend(); // có loadToken riêng bên trong → đã bỏ ở fix #3
            loadFriendNamesFromBackend();
            fetchCurrentUserProfile();
        }

        // Luôn reload: bài đăng, badge, avatar (nhẹ, cần cập nhật khi quay lại)
        loadPostsFromApi();
        loadUnreadNotificationCount();
        loadStatusHeaderAvatar();

        // Resubscribe WebSocket (chỉ khi connected)
        subscribeToRealtimeEvents();
        highlightTab(btnHome);
    }

    /**
     * Đồng bộ sở thích từ backend vào SharedPreferences.
     * Đảm bảo tag filter luôn có dữ liệu.
     */
    private void syncInterestsFromBackend() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
        // loadToken() ĐÃ được gọi từ onResume() trước đó, không gọi lại ở đây
        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);
        userApi.getInterests().enqueue(new Callback<com.example.weconnect.models.ApiResponse<java.util.List<String>>>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<String>>> call,
                                   retrofit2.Response<com.example.weconnect.models.ApiResponse<java.util.List<String>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    java.util.List<String> interests = response.body().getResult();
                    if (!interests.isEmpty()) {
                        prefs.edit().putString("user_interests", String.join(",", interests)).apply();
                        // Reload posts với filter mới
                        loadPostsFromApi();
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<String>>> call, Throwable t) {
                // Bỏ qua - dùng dữ liệu cũ
            }
        });
    }

    private void loadUnreadNotificationCount() {
        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();
        if (token == null || tvNotifBadge == null) return;

        com.example.weconnect.api.NotificationApiService notifApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.NotificationApiService.class);

        notifApi.getUnreadCount().enqueue(new Callback<ApiResponse<Integer>>() {
            @Override
            public void onResponse(Call<ApiResponse<Integer>> call,
                                   Response<ApiResponse<Integer>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    int count = response.body().getResult();
                    runOnUiThread(() -> updateBadge(count));
                } else {
                    // Fallback: try counting from notifications list
                    loadUnreadCountFallback();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Integer>> call, Throwable t) {
                loadUnreadCountFallback();
            }
        });
    }

    private void loadUnreadCountFallback() {
        com.example.weconnect.api.NotificationApiService notifApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.NotificationApiService.class);

        notifApi.getNotifications().enqueue(new Callback<ApiResponse<java.util.List<com.example.weconnect.models.NotificationItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<java.util.List<com.example.weconnect.models.NotificationItem>>> call,
                                   Response<ApiResponse<java.util.List<com.example.weconnect.models.NotificationItem>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    int count = 0;
                    for (com.example.weconnect.models.NotificationItem item : response.body().getResult()) {
                        if (!item.isRead()) count++;
                    }
                    int finalCount = count;
                    runOnUiThread(() -> updateBadge(finalCount));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<java.util.List<com.example.weconnect.models.NotificationItem>>> call, Throwable t) {}
        });
    }

    private void updateBadge(int count) {
        BadgeManager.setCount(count);
        BadgeManager.applyBadge(tvNotifBadge);
    }

    private void setupActivityResultLauncher() {
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
                        long endTimeMillis = data.getLongExtra("post_end_time", System.currentTimeMillis() + 24L * 60L * 60L * 1000L);
                        String activityStartIso = data.getStringExtra("post_activity_start_iso");
                        String activityEndIso = data.getStringExtra("post_activity_end_iso");
                        String activityTimeType = data.getStringExtra("post_activity_time_type");

                        // Gọi API tạo bài đăng mới
                        createPostViaApi(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso, activityTimeType);
                    }
                }
        );
    }

    private void createPostViaApi(String content, String tag, String location, int maxMembers, String imageUri, long endTimeMillis,
                                  String activityStartIso, String activityEndIso, String activityTimeType) {
        if (imageUri != null) {
            uploadImageAndCreatePost(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso, activityTimeType);
        } else {
            sendCreatePost(content, tag, location, maxMembers, null, endTimeMillis, activityStartIso, activityEndIso, activityTimeType);
        }
    }

    private void uploadImageAndCreatePost(String content, String tag, String location, int maxMembers, String imageUri, long endTimeMillis,
                                          String activityStartIso, String activityEndIso, String activityTimeType) {
        try {
            android.net.Uri uri = android.net.Uri.parse(imageUri);
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Không thể đọc ảnh", Toast.LENGTH_SHORT).show();
                sendCreatePost(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso, activityTimeType);
                return;
            }
            byte[] bytes = readAllBytes(inputStream);
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
                    sendCreatePost(content, tag, location, maxMembers, serverUrl, endTimeMillis, activityStartIso, activityEndIso, activityTimeType);
                }

                @Override
                public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                    android.util.Log.e("UPLOAD_IMAGE", "Failed: " + t.getMessage());
                    sendCreatePost(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso, activityTimeType);
                }
            });
        } catch (Exception e) {
            android.util.Log.e("UPLOAD_IMAGE", "Error: " + e.getMessage());
            sendCreatePost(content, tag, location, maxMembers, imageUri, endTimeMillis, activityStartIso, activityEndIso, activityTimeType);
        }
    }

    private byte[] readAllBytes(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private void sendCreatePost(String content, String tag, String location, int maxMembers, String imageUrl, long endTimeMillis,
                                String activityStartIso, String activityEndIso, String activityTimeType) {
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
        if (activityTimeType != null) {
            body.put("activityTimeType", activityTimeType);
        }

        final String postTag = tag;
        final String currentUser = RetrofitClient.getUserName(this);

        postApiService.createPost(body).enqueue(new Callback<ApiResponse<PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PostResponse>> call,
                                   Response<ApiResponse<PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(MainActivity.this, "Đã tạo bài đăng!", Toast.LENGTH_SHORT).show();
                    loadPostsFromApi();
                } else {
                    String errorMsg = "Không thể tạo bài đăng";
                    if (response.body() != null && response.body().getMessage() != null) {
                        errorMsg = response.body().getMessage();
                    }
                    android.util.Log.e("CREATE_POST", "Error: " + response.code() + " - " + errorMsg);
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PostResponse>> call, Throwable t) {
                android.util.Log.e("CREATE_POST", "Failure: " + t.getMessage());
                Toast.makeText(MainActivity.this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initViews() {
        ivAdd = findViewById(R.id.ivAdd);
        ivSearch = findViewById(R.id.ivSearch);
        btnHome = findViewById(R.id.btnHome);
        btnMessages = findViewById(R.id.btnMessages);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnProfile = findViewById(R.id.btnProfile);
        rvPosts = findViewById(R.id.rvPosts);
        statusHeader = findViewById(R.id.statusHeader);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);
        loadStatusHeaderAvatar();

        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout =
                findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setColorSchemeColors(0xFFFF4D6D);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadPostsFromApi();
            swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 1000);
        });
    }

    private void setupClickListeners() {
        ivAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreatePostActivity.class);
            createPostLauncher.launch(intent);
        });

        ivSearch.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        statusHeader.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreatePostActivity.class);
            createPostLauncher.launch(intent);
        });

        btnHome.setOnClickListener(v -> {
            highlightTab(btnHome);
            showToast("Trang ch\u1ee7");
        });

        btnMessages.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UserProfileActivity.class);
            intent.putExtra("username", RetrofitClient.getUserName(this));
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }

    private void setupRecyclerView() {
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        postList = new ArrayList<>();
        postAdapter = new PostAdapter(this, postList);
        rvPosts.setAdapter(postAdapter);

        // Load từ API
        loadPostsFromApi();
    }

    private void loadPostsFromApi() {
        postApiService.getActivePosts().enqueue(new Callback<ApiResponse<List<PostResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PostResponse>>> call,
                                   Response<ApiResponse<List<PostResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<PostResponse> postResponses = response.body().getResult();
                    List<Post> allPosts = new ArrayList<>();
                    if (postResponses != null) {
                        for (PostResponse pr : postResponses) {
                            allPosts.add(pr.toPost());
                        }
                    }
                    postList.clear();
                    postList.addAll(filterAndSortPosts(allPosts));
                    postAdapter.notifyDataSetChanged();
                } else {
                    loadPostsFallback();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PostResponse>>> call, Throwable t) {
                loadPostsFallback();
            }
        });
    }

    private void loadPostsFallback() {
        postList.clear();
        postAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Không thể tải bài đăng. Hãy kiểm tra kết nối server!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Lọc và sắp xếp bài đăng theo Priority Feed Sorting:
     * 1. Hiển thị TẤT CẢ bài còn hạn (không bị lọc theo tag)
     * 2. Nhóm 1: bài có tag trùng sở thích của user → đẩy lên đầu
     * 3. Nhóm 2: bài không trùng tag → hiển thị phía sau
     * 4. Trong mỗi nhóm: giữ nguyên thứ tự createdAt DESC từ backend
     */
    private List<Post> filterAndSortPosts(List<Post> allPosts) {
        // Lấy danh sách sở thích của user từ SharedPreferences
        Set<String> userInterests = getUserInterestTags();

        // Nhóm 1: bài trùng tag sở thích — hiển thị đầu tiên
        List<Post> matchedPosts = new ArrayList<>();
        // Nhóm 2: bài không trùng tag (hoặc không có tag) — hiển thị phía sau
        List<Post> otherPosts = new ArrayList<>();

        for (Post post : allPosts) {
            // Bỏ qua bài hết hạn hoặc đã đóng
            if (post.isExpired() || post.isArchived()) continue;

            // Kiểm tra bài có tag trùng sở thích của user không
            boolean matchTag = false;
            if (!userInterests.isEmpty()
                    && post.getInterestTag() != null
                    && !post.getInterestTag().trim().isEmpty()) {
                for (String interest : userInterests) {
                    if (interest.equalsIgnoreCase(post.getInterestTag().trim())) {
                        matchTag = true;
                        break;
                    }
                }
            }

            if (matchTag) {
                matchedPosts.add(post);
            } else {
                otherPosts.add(post);
            }
        }

        // Gộp: nhóm trùng sở thích lên trước, nhóm còn lại theo sau
        // Thứ tự trong mỗi nhóm đã đúng do backend sắp sẵn theo createdAt DESC
        List<Post> result = new ArrayList<>(matchedPosts);
        result.addAll(otherPosts);
        return result;
    }

    private Set<String> getUserInterestTags() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
        String saved = prefs.getString("user_interests", "");
        
        // Nếu trống, thử load từ API (giả định có endpoint lấy profile)
        if (saved.isEmpty()) {
            // Logic load từ API có thể thêm ở đây nếu cần đồng bộ
        }

        Set<String> tags = new HashSet<>();
        if (!saved.isEmpty()) {
            for (String s : saved.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) tags.add(trimmed);
            }
        }
        return tags;
    }

    private Set<String> cachedFriendNames = new java.util.HashSet<>();

    private Set<String> getFriendNames() {
        return cachedFriendNames;
    }

    private void loadFriendNamesFromBackend() {
        RetrofitClient.loadToken(this);
        com.example.weconnect.api.FriendApiService friendApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.FriendApiService.class);
        friendApi.getFriends().enqueue(new Callback<com.example.weconnect.models.ApiResponse<java.util.List<java.util.Map<String, Object>>>>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<java.util.Map<String, Object>>>> call,
                                   retrofit2.Response<com.example.weconnect.models.ApiResponse<java.util.List<java.util.Map<String, Object>>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    Set<String> names = new java.util.HashSet<>();
                    for (java.util.Map<String, Object> friend : response.body().getResult()) {
                        Object name = friend.get("fullName");
                        if (name != null) names.add(name.toString());
                    }
                    cachedFriendNames = names;
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<java.util.Map<String, Object>>>> call, Throwable t) {
                // Bỏ qua lỗi
            }
        });
    }

    private void highlightTab(FrameLayout selectedTab) {
        // Reset all tabs: full opacity + secondary tint
        setTabTint(btnHome, R.color.text_secondary);
        setTabTint(btnMessages, R.color.text_secondary);
        setTabTint(btnNotifications, R.color.text_secondary);
        setTabTint(btnProfile, R.color.text_secondary);
        // Highlight selected tab with red tint
        setTabTint(selectedTab, R.color.primary_pink);
    }

    private void setTabTint(FrameLayout tab, int colorResId) {
        tab.setAlpha(1.0f);
        // Find the ImageView inside the FrameLayout (first child)
        if (tab.getChildAt(0) instanceof ImageView) {
            ImageView icon = (ImageView) tab.getChildAt(0);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(colorResId, getTheme())));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "weconnect_channel",
                    "WeConnect Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void fetchAndRegisterFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return;
                    String fcmToken = task.getResult();
                    java.util.Map<String, String> body = new java.util.HashMap<>();
                    body.put("fcmToken", fcmToken);
                    RetrofitClient.getClient()
                            .create(com.example.weconnect.api.UserApiService.class)
                            .updateFcmToken(body)
                            .enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<Void>>() {
                                @Override
                                public void onResponse(
                                        retrofit2.Call<com.example.weconnect.models.ApiResponse<Void>> call,
                                        retrofit2.Response<com.example.weconnect.models.ApiResponse<Void>> response) {}

                                @Override
                                public void onFailure(
                                        retrofit2.Call<com.example.weconnect.models.ApiResponse<Void>> call,
                                        Throwable t) {}
                            });
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2001 && resultCode == RESULT_OK && data != null) {
            long editPostId = data.getLongExtra("edit_post_id", -1);
            if (editPostId != -1) {
                updatePostViaApi(editPostId, data);
            }
        }
    }

    private void updatePostViaApi(long postId, Intent data) {
        String imageUri = data.getStringExtra("post_image_uri");
        if (imageUri != null && imageUri.startsWith("content://")) {
            // Upload image first, then update post
            try {
                android.net.Uri uri = android.net.Uri.parse(imageUri);
                java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    byte[] bytes = readAllBytes(inputStream);
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
                            sendUpdatePost(postId, data, serverUrl);
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                            sendUpdatePost(postId, data, null);
                        }
                    });
                    return;
                }
            } catch (Exception e) {
                android.util.Log.e("UPLOAD_IMAGE", "Error: " + e.getMessage());
            }
        }
        // No image or already a server URL
        sendUpdatePost(postId, data, imageUri);
    }

    private void sendUpdatePost(long postId, Intent data, String imageUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", data.getStringExtra("post_content"));
        body.put("interestTag", data.getStringExtra("post_tag"));
        body.put("location", data.getStringExtra("post_location"));
        body.put("maxMembers", data.getIntExtra("post_max_members", 10));
        if (imageUrl != null) body.put("imageUrl", imageUrl);

        long endTimeMillis = data.getLongExtra("post_end_time", System.currentTimeMillis() + 24L * 60L * 60L * 1000L);
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        String activityStartIso = data.getStringExtra("post_activity_start_iso");
        String activityEndIso = data.getStringExtra("post_activity_end_iso");
        String activityTimeType = data.getStringExtra("post_activity_time_type");
        body.put("startTime", activityStartIso != null ? activityStartIso : isoFormat.format(new Date()));
        body.put("endTime", isoFormat.format(new Date(endTimeMillis)));
        if (activityEndIso != null) {
            body.put("activityEndTime", activityEndIso);
        }
        if (activityTimeType != null) {
            body.put("activityTimeType", activityTimeType);
        }

        postApiService.updatePost(postId, body).enqueue(new Callback<ApiResponse<PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PostResponse>> call,
                                   Response<ApiResponse<PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(MainActivity.this, "Đã cập nhật bài viết!", Toast.LENGTH_SHORT).show();
                    loadPostsFromApi();
                } else {
                    Toast.makeText(MainActivity.this, "Không thể cập nhật bài viết", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PostResponse>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchCurrentUserProfile() {
        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);
        userApi.getMyProfile().enqueue(new Callback<ApiResponse<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<java.util.Map<String, Object>>> call,
                                   Response<ApiResponse<java.util.Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResult() != null) {
                    java.util.Map<String, Object> profile = response.body().getResult();
                    Object avatarObj = profile.get("avatarUrl");
                    if (avatarObj != null && !avatarObj.toString().isEmpty()) {
                        RetrofitClient.saveAvatarUrl(MainActivity.this, avatarObj.toString());
                    }
                    Object nameObj = profile.get("fullName");
                    if (nameObj != null && !nameObj.toString().isEmpty()) {
                        RetrofitClient.saveUserName(MainActivity.this, nameObj.toString());
                    }
                    loadStatusHeaderAvatar();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                // giữ dữ liệu cache hiện tại
            }
        });
    }

    private void loadStatusHeaderAvatar() {
        if (statusHeader == null) return;
        ImageView ivStatusAvatar = statusHeader.findViewById(R.id.ivStatusAvatar);
        if (ivStatusAvatar == null) return;
        String avatarUrl = RetrofitClient.getAvatarUrl(this);
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (avatarUrl.startsWith("/")) {
                avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
            }
            com.bumptech.glide.Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(ivStatusAvatar);
        } else {
            ivStatusAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }
    }

    private void subscribeToRealtimeEvents() {
        WebSocketManager ws = WebSocketManager.getInstance();
        if (!ws.isConnected()) return;

        // Nhận bài post mới từ user khác
        ws.subscribeToFeed(json -> {
            try {
                PostResponse postResp = new Gson().fromJson(json, PostResponse.class);
                if (postResp == null || postResp.getId() == null) return;

                // Bỏ qua post của chính mình (đã được thêm qua REST response)
                long myId = RetrofitClient.getUserId(this);
                if (postResp.getAuthorId() != null && postResp.getAuthorId() == myId) return;

                // Tránh trùng lặp
                String newPostId = String.valueOf(postResp.getId());
                for (Post p : postList) {
                    if (newPostId.equals(p.getId())) return;
                }

                Post newPost = postResp.toPost();
                // Áp dụng cached avatar nếu có
                if (postResp.getAuthorId() != null) {
                    String cachedAvatar = RetrofitClient.getCachedAvatarForUser(postResp.getAuthorId());
                    if (cachedAvatar != null && !cachedAvatar.isEmpty()) newPost.setAvatarUrl(cachedAvatar);
                }
                postList.add(0, newPost);
                postAdapter.notifyItemInserted(0);
                rvPosts.scrollToPosition(0);
            } catch (Exception ignored) {}
        });

        // Nhận notification mới → cập nhật badge
        ws.subscribeToNotifications(json -> {
            BadgeManager.increment();
            runOnUiThread(() -> updateBadge(BadgeManager.getCount()));
        });

        // Nhận cập nhật avatar user bất kỳ
        ws.subscribeToAvatarUpdates(json -> {
            try {
                Gson gson = new Gson();
                @SuppressWarnings("unchecked")
                Map<String, Object> map = gson.fromJson(json, Map.class);
                if (map == null) return;

                long userId = ((Number) map.get("userId")).longValue();
                String avatarUrl = (String) map.get("avatarUrl");
                if (avatarUrl == null || avatarUrl.isEmpty()) return;

                // Cập nhật global cache cho tất cả user
                RetrofitClient.cacheAvatarForUser(userId, avatarUrl);

                // Nếu là user hiện tại, cập nhật personal cache và header
                long myId = RetrofitClient.getUserId(this);
                if (userId == myId) {
                    RetrofitClient.saveAvatarUrl(this, avatarUrl);
                    loadStatusHeaderAvatar();
                }

                // Refresh tất cả post hiển thị
                if (postAdapter != null) postAdapter.notifyDataSetChanged();
            } catch (Exception ignored) {}
        });
    }
}
