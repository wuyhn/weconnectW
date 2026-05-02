package com.example.weconnect.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.PostAdapter;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestoreFriendRepository;
import com.example.weconnect.api.FirestoreNotificationRepository;
import com.example.weconnect.api.FirestorePostRepository;
import com.example.weconnect.models.Post;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ImageView ivAdd, ivSearch;
    private FrameLayout btnHome, btnMessages, btnNotifications, btnProfile;
    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private List<Post> postList;
    private View statusHeader;
    private android.widget.TextView tvNotifBadge;

    private FirestorePostRepository postRepo;
    private FirestoreNotificationRepository notifRepo;
    private FirestoreFriendRepository friendRepo;
    private ListenerRegistration notifBadgeListener;

    private ActivityResultLauncher<Intent> createPostLauncher;

    // Cache friend UIDs để ưu tiên bài bạn bè lên feed
    private Set<String> cachedFriendIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Kiểm tra đăng nhập
        if (!FirebaseManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        postRepo  = new FirestorePostRepository();
        notifRepo = new FirestoreNotificationRepository();
        friendRepo = new FirestoreFriendRepository();

        setupActivityResultLauncher();
        initViews();
        setupClickListeners();
        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFriendIds();
        loadPostsFromFirestore();
        startNotifBadgeListener();
        highlightTab(btnHome);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
            notifBadgeListener = null;
        }
    }

    // ======================================================================
    // Realtime Notification Badge (thay polling cũ)
    // ======================================================================
    private void startNotifBadgeListener() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null || tvNotifBadge == null) return;
        if (notifBadgeListener != null) notifBadgeListener.remove();

        notifBadgeListener = notifRepo.listenUnreadCount(uid, count ->
            runOnUiThread(() -> updateBadge(count))
        );
    }

    private void updateBadge(int count) {
        if (tvNotifBadge == null) return;
        if (count > 0) {
            tvNotifBadge.setVisibility(View.VISIBLE);
            tvNotifBadge.setText(count > 99 ? "99+" : String.valueOf(count));
        } else {
            tvNotifBadge.setVisibility(View.GONE);
        }
    }

    // ======================================================================
    // Load Posts từ Firestore
    // ======================================================================
    private void loadPostsFromFirestore() {
        postRepo.getActivePosts(new FirestorePostRepository.PostsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> posts) {
                List<Post> allPosts = new ArrayList<>();
                Set<String> userInterests = getUserInterestTags();

                for (Map<String, Object> p : posts) {
                    Post post = mapToPost(p);
                    if (post != null) allPosts.add(post);
                }

                postList.clear();
                postList.addAll(filterAndSortPosts(allPosts, userInterests));
                runOnUiThread(() -> postAdapter.notifyDataSetChanged());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Load posts error: " + error);
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Không thể tải bài đăng", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    /** Chuyển Map<String,Object> từ Firestore sang Post model */
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

            Timestamp endTs  = (Timestamp) p.get("endTime");
            Timestamp startTs = (Timestamp) p.get("startTime");
            long endMillis   = endTs != null ? endTs.toDate().getTime() : System.currentTimeMillis() + 86400000L;
            long startMillis = startTs != null ? startTs.toDate().getTime() : System.currentTimeMillis();

            String currentUid = FirebaseManager.getCurrentUserId();
            String authorId = (String) p.get("authorId");
            boolean isMyPost = currentUid != null && currentUid.equals(authorId);

            Post post = new Post(id, authorName, "vừa xong", content, tag, location,
                    0, 0, memberCount, 0, 0, maxMembers, isMyPost,
                    startMillis, endMillis, archived);
            post.setAuthorId(authorId != null ? authorId.hashCode() : 0);
            post.setAvatarUrl(authorAvatarUrl);
            if (imageUrl != null && !imageUrl.isEmpty()) post.setPostImageUri(imageUrl);
            return post;
        } catch (Exception e) {
            Log.e(TAG, "mapToPost error: " + e.getMessage());
            return null;
        }
    }

    private List<Post> filterAndSortPosts(List<Post> all, Set<String> userInterests) {
        List<Post> friendPosts = new ArrayList<>();
        List<Post> otherPosts  = new ArrayList<>();

        for (Post post : all) {
            if (post.isExpired() || post.isArchived()) continue;

            // Lọc theo interest tag (nếu user đã chọn tags)
            if (!userInterests.isEmpty() && post.getInterestTag() != null && !post.getInterestTag().isEmpty()) {
                boolean match = false;
                for (String t : userInterests) {
                    if (t.equalsIgnoreCase(post.getInterestTag().trim())) { match = true; break; }
                }
                if (!match && !post.isJoined() && !post.isPendingApproval()) continue;
            }

            // Ưu tiên bài của bạn bè
            String authorIdStr = String.valueOf(post.getAuthorId());
            if (cachedFriendIds.contains(authorIdStr)) friendPosts.add(post);
            else otherPosts.add(post);
        }

        List<Post> result = new ArrayList<>(friendPosts);
        result.addAll(otherPosts);
        return result;
    }

    private Set<String> getUserInterestTags() {
        SharedPreferences prefs = getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
        String saved = prefs.getString("user_interests", "");
        Set<String> tags = new HashSet<>();
        if (!saved.isEmpty()) {
            for (String s : saved.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) tags.add(t);
            }
        }
        return tags;
    }

    private void loadFriendIds() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;
        friendRepo.getFriends(uid, new FirestoreFriendRepository.FriendsCallback() {
            @Override public void onSuccess(List<Map<String, Object>> friends) {
                cachedFriendIds.clear();
                for (Map<String, Object> f : friends) {
                    String u1 = (String) f.get("user1Id");
                    String u2 = (String) f.get("user2Id");
                    if (u1 != null && !u1.equals(uid)) cachedFriendIds.add(u1);
                    if (u2 != null && !u2.equals(uid)) cachedFriendIds.add(u2);
                }
            }
            @Override public void onError(String e) {}
        });
    }

    // ======================================================================
    // Create Post via Firebase Storage + Firestore
    // ======================================================================
    private void setupActivityResultLauncher() {
        createPostLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String content   = data.getStringExtra("post_content");
                    String tag       = data.getStringExtra("post_tag");
                    String location  = data.getStringExtra("post_location");
                    int maxMembers   = data.getIntExtra("post_max_members", 10);
                    String imageUri  = data.getStringExtra("post_image_uri");
                    long endTimeMs   = data.getLongExtra("post_end_time",
                            System.currentTimeMillis() + 86400000L);

                    createPostWithFirebase(content, tag, location, maxMembers, imageUri, endTimeMs);
                }
            }
        );
    }

    private void createPostWithFirebase(String content, String tag, String location,
                                         int maxMembers, String imageUri, long endTimeMs) {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;

        // Lấy profile user
        FirebaseManager.getFirestore().collection("users").document(uid).get()
            .addOnSuccessListener(doc -> {
                String authorName = doc.exists() ? doc.getString("fullName") : "Người dùng";
                String avatarUrl  = doc.exists() ? doc.getString("avatarUrl") : "";

                if (imageUri != null && imageUri.startsWith("content://")) {
                    // Upload ảnh lên Firebase Storage trước
                    uploadImageToStorage(imageUri, storageUrl ->
                        doCreatePost(uid, authorName, avatarUrl, content, tag, location,
                                maxMembers, storageUrl, endTimeMs)
                    );
                } else {
                    doCreatePost(uid, authorName, avatarUrl, content, tag, location,
                            maxMembers, imageUri, endTimeMs);
                }
            });
    }

    interface UploadCallback { void onDone(String url); }

    private void uploadImageToStorage(String contentUri, UploadCallback callback) {
        try {
            android.net.Uri uri = android.net.Uri.parse(contentUri);
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) { callback.onDone(null); return; }
            byte[] bytes = readAllBytes(is);
            is.close();

            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "image/jpeg";
            String ext = mime.contains("png") ? ".png" : ".jpg";
            String path = "posts/" + FirebaseManager.getCurrentUserId()
                    + "_" + System.currentTimeMillis() + ext;

            StorageReference ref = FirebaseStorage.getInstance().getReference(path);
            ref.putBytes(bytes)
                .addOnSuccessListener(snap ->
                    ref.getDownloadUrl().addOnSuccessListener(downloadUri ->
                        callback.onDone(downloadUri.toString())
                    )
                )
                .addOnFailureListener(e -> callback.onDone(null));
        } catch (Exception e) {
            Log.e(TAG, "Upload error: " + e.getMessage());
            callback.onDone(null);
        }
    }

    private void doCreatePost(String uid, String authorName, String avatarUrl,
                               String content, String tag, String location,
                               int maxMembers, String imageUrl, long endTimeMs) {
        Timestamp startTs = Timestamp.now();
        Timestamp endTs   = new Timestamp(new Date(endTimeMs));

        postRepo.createPost(uid, authorName, avatarUrl, content, tag, location,
            imageUrl, maxMembers, startTs, endTs,
            new FirestorePostRepository.ActionCallback() {
                @Override public void onSuccess(String postId) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Đã tạo bài đăng!", Toast.LENGTH_SHORT).show();
                        loadPostsFromFirestore();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Lỗi: " + error, Toast.LENGTH_SHORT).show()
                    );
                }
            });
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] data = new byte[4096]; int n;
        while ((n = is.read(data)) != -1) buf.write(data, 0, n);
        return buf.toByteArray();
    }

    // ======================================================================
    // Views / Navigation
    // ======================================================================
    private void initViews() {
        ivAdd      = findViewById(R.id.ivAdd);
        ivSearch   = findViewById(R.id.ivSearch);
        btnHome    = findViewById(R.id.btnHome);
        btnMessages      = findViewById(R.id.btnMessages);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnProfile = findViewById(R.id.btnProfile);
        rvPosts    = findViewById(R.id.rvPosts);
        statusHeader = findViewById(R.id.statusHeader);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);

        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe =
                findViewById(R.id.swipeRefreshLayout);
        swipe.setColorSchemeColors(0xFFFF4D6D);
        swipe.setOnRefreshListener(() -> {
            loadPostsFromFirestore();
            swipe.postDelayed(() -> swipe.setRefreshing(false), 1000);
        });
    }

    private void setupClickListeners() {
        ivAdd.setOnClickListener(v ->
            createPostLauncher.launch(new Intent(this, CreatePostActivity.class))
        );
        ivSearch.setOnClickListener(v ->
            startActivity(new Intent(this, SearchActivity.class))
        );
        statusHeader.setOnClickListener(v ->
            createPostLauncher.launch(new Intent(this, CreatePostActivity.class))
        );
        btnHome.setOnClickListener(v -> {
            highlightTab(btnHome);
            Toast.makeText(this, "Trang chủ", Toast.LENGTH_SHORT).show();
        });
        btnMessages.setOnClickListener(v -> {
            highlightTab(btnMessages);
            startActivity(new Intent(this, ChatListActivity.class));
        });
        btnNotifications.setOnClickListener(v -> {
            highlightTab(btnNotifications);
            startActivity(new Intent(this, NotificationsActivity.class));
        });
        btnProfile.setOnClickListener(v -> {
            highlightTab(btnProfile);
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("user_uid", FirebaseManager.getCurrentUserId());
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        postList    = new ArrayList<>();
        postAdapter = new PostAdapter(this, postList);
        rvPosts.setAdapter(postAdapter);
    }

    private void highlightTab(FrameLayout tab) {
        setTabTint(btnHome, R.color.text_secondary);
        setTabTint(btnMessages, R.color.text_secondary);
        setTabTint(btnNotifications, R.color.text_secondary);
        setTabTint(btnProfile, R.color.text_secondary);
        setTabTint(tab, R.color.primary_pink);
    }

    private void setTabTint(FrameLayout tab, int colorResId) {
        tab.setAlpha(1.0f);
        if (tab.getChildAt(0) instanceof ImageView) {
            ((ImageView) tab.getChildAt(0)).setImageTintList(
                android.content.res.ColorStateList.valueOf(
                    getResources().getColor(colorResId, getTheme())));
        }
    }
}
