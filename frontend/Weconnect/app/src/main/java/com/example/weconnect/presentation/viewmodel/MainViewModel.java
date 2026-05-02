package com.example.weconnect.presentation.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestoreFriendRepository;
import com.example.weconnect.data.repository.FirestoreNotificationRepository;
import com.example.weconnect.data.repository.FirestorePostRepository;
import com.example.weconnect.domain.model.Post;
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

public class MainViewModel extends AndroidViewModel {

    private final FirestorePostRepository postRepo;
    private final FirestoreNotificationRepository notifRepo;
    private final FirestoreFriendRepository friendRepo;

    private ListenerRegistration notifBadgeListener;
    private final Set<String> cachedFriendIds = new HashSet<>();

    private final MutableLiveData<List<Post>> _posts = new MutableLiveData<>();
    public LiveData<List<Post>> posts = _posts;

    private final MutableLiveData<Integer> _unreadNotifCount = new MutableLiveData<>();
    public LiveData<Integer> unreadNotifCount = _unreadNotifCount;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final MutableLiveData<Boolean> _postCreated = new MutableLiveData<>();
    public LiveData<Boolean> postCreated = _postCreated;

    public MainViewModel(@NonNull Application application) {
        super(application);
        postRepo = new FirestorePostRepository();
        notifRepo = new FirestoreNotificationRepository();
        friendRepo = new FirestoreFriendRepository();
    }

    public void startNotifBadgeListener() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;
        if (notifBadgeListener != null) notifBadgeListener.remove();

        notifBadgeListener = notifRepo.listenUnreadCount(uid, count -> {
            _unreadNotifCount.postValue(count);
        });
    }

    public void stopNotifBadgeListener() {
        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
            notifBadgeListener = null;
        }
    }

    public void loadFriendIdsAndPosts() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;
        friendRepo.getFriends(uid, new FirestoreFriendRepository.FriendsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> friends) {
                cachedFriendIds.clear();
                for (Map<String, Object> f : friends) {
                    String u1 = (String) f.get("user1Id");
                    String u2 = (String) f.get("user2Id");
                    if (u1 != null && !u1.equals(uid)) cachedFriendIds.add(u1);
                    if (u2 != null && !u2.equals(uid)) cachedFriendIds.add(u2);
                }
                loadPostsFromFirestore();
            }

            @Override
            public void onError(String e) {
                loadPostsFromFirestore(); // fallback
            }
        });
    }

    public void loadPostsFromFirestore() {
        postRepo.getActivePosts(new FirestorePostRepository.PostsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> fetchedPosts) {
                List<Post> allPosts = new ArrayList<>();
                Set<String> userInterests = getUserInterestTags();

                for (Map<String, Object> p : fetchedPosts) {
                    Post post = mapToPost(p);
                    if (post != null) allPosts.add(post);
                }

                _posts.postValue(filterAndSortPosts(allPosts, userInterests));
            }

            @Override
            public void onError(String error) {
                _error.postValue("Không thể tải bài đăng: " + error);
            }
        });
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
            return null;
        }
    }

    private List<Post> filterAndSortPosts(List<Post> all, Set<String> userInterests) {
        List<Post> friendPosts = new ArrayList<>();
        List<Post> otherPosts  = new ArrayList<>();

        for (Post post : all) {
            if (post.isExpired() || post.isArchived()) continue;

            if (!userInterests.isEmpty() && post.getInterestTag() != null && !post.getInterestTag().isEmpty()) {
                boolean match = false;
                for (String t : userInterests) {
                    if (t.equalsIgnoreCase(post.getInterestTag().trim())) { match = true; break; }
                }
                if (!match && !post.isJoined() && !post.isPendingApproval()) continue;
            }

            String authorIdStr = String.valueOf(post.getAuthorId());
            if (cachedFriendIds.contains(authorIdStr)) friendPosts.add(post);
            else otherPosts.add(post);
        }

        List<Post> result = new ArrayList<>(friendPosts);
        result.addAll(otherPosts);
        return result;
    }

    private Set<String> getUserInterestTags() {
        SharedPreferences prefs = getApplication().getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
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

    public void createPostWithFirebase(String content, String tag, String location,
                                        int maxMembers, String imageUri, long endTimeMs) {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;

        FirebaseManager.getFirestore().collection("users").document(uid).get()
            .addOnSuccessListener(doc -> {
                String authorName = doc.exists() ? doc.getString("fullName") : "Người dùng";
                String avatarUrl  = doc.exists() ? doc.getString("avatarUrl") : "";

                if (imageUri != null && imageUri.startsWith("content://")) {
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

    private void uploadImageToStorage(String contentUri, UploadCallback callback) {
        try {
            Uri uri = Uri.parse(contentUri);
            InputStream is = getApplication().getContentResolver().openInputStream(uri);
            if (is == null) { callback.onDone(null); return; }
            byte[] bytes = readAllBytes(is);
            is.close();

            String mime = getApplication().getContentResolver().getType(uri);
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
                    _postCreated.postValue(true);
                    loadPostsFromFirestore();
                }
                @Override public void onError(String error) {
                    _error.postValue("Lỗi tạo bài đăng: " + error);
                }
            });
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] data = new byte[4096]; int n;
        while ((n = is.read(data)) != -1) buf.write(data, 0, n);
        return buf.toByteArray();
    }

    interface UploadCallback { void onDone(String url); }
}
