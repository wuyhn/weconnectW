package com.example.weconnect.presentation.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestorePostRepository;
import com.example.weconnect.data.repository.FirestoreUserRepository;
import com.example.weconnect.domain.model.Post;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProfileViewModel extends AndroidViewModel {

    private final FirestoreUserRepository userRepo;
    private final FirestorePostRepository postRepo;

    private final MutableLiveData<Map<String, Object>> _userProfile = new MutableLiveData<>();
    public LiveData<Map<String, Object>> userProfile = _userProfile;

    private final MutableLiveData<List<String>> _interests = new MutableLiveData<>();
    public LiveData<List<String>> interests = _interests;

    private final MutableLiveData<List<Post>> _activePosts = new MutableLiveData<>();
    public LiveData<List<Post>> activePosts = _activePosts;

    private final MutableLiveData<List<Post>> _myActivities = new MutableLiveData<>();
    public LiveData<List<Post>> myActivities = _myActivities;

    private final MutableLiveData<List<Post>> _relatedPosts = new MutableLiveData<>();
    public LiveData<List<Post>> relatedPosts = _relatedPosts;

    private final MutableLiveData<List<Post>> _archivedPosts = new MutableLiveData<>();
    public LiveData<List<Post>> archivedPosts = _archivedPosts;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final MutableLiveData<String> _successMessage = new MutableLiveData<>();
    public LiveData<String> successMessage = _successMessage;

    private final MutableLiveData<Boolean> _actionStatus = new MutableLiveData<>();
    public LiveData<Boolean> actionStatus = _actionStatus;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        userRepo = new FirestoreUserRepository();
        postRepo = new FirestorePostRepository();
    }

    public void loadProfile(String uid) {
        if (uid == null) return;
        userRepo.getUserProfile(uid, new FirestoreUserRepository.UserCallback() {
            @Override
            public void onSuccess(Map<String, Object> profile) {
                _userProfile.postValue(profile);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
            }
        });
    }

    public void loadInterests(String uid) {
        if (uid == null) return;
        userRepo.getInterests(uid, new FirestoreUserRepository.InterestsCallback() {
            @Override
            public void onSuccess(List<String> interests) {
                _interests.postValue(interests);
            }

            @Override
            public void onError(String err) {
                _interests.postValue(new ArrayList<>());
            }
        });
    }

    public void loadActivePosts(String uid) {
        if (uid == null) return;
        postRepo.getUserPosts(uid, new FirestorePostRepository.PostsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> posts) {
                List<Post> result = new ArrayList<>();
                for (Map<String, Object> p : posts) {
                    Post post = mapToPost(p);
                    if (post != null) result.add(post);
                }
                _activePosts.postValue(result);
            }

            @Override
            public void onError(String err) {
                _activePosts.postValue(new ArrayList<>());
            }
        });
    }

    public void loadMyActivities(String uid) {
        if (uid == null) return;
        postRepo.getMyActivities(uid, new FirestorePostRepository.PostsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> posts) {
                List<Post> result = new ArrayList<>();
                for (Map<String, Object> p : posts) {
                    Post post = mapToPost(p);
                    if (post != null) result.add(post);
                }
                _myActivities.postValue(result);
            }

            @Override
            public void onError(String err) {
                _myActivities.postValue(new ArrayList<>());
            }
        });
    }

    public void loadRelatedPosts(String uid, String viewedUid) {
        if (uid == null) return;
        userRepo.getInterests(uid, new FirestoreUserRepository.InterestsCallback() {
            @Override
            public void onSuccess(List<String> interests) {
                if (interests == null || interests.isEmpty()) {
                    _relatedPosts.postValue(new ArrayList<>());
                    return;
                }
                fetchAndFilterRelatedPosts(interests, viewedUid);
            }

            @Override
            public void onError(String err) {
                _relatedPosts.postValue(new ArrayList<>());
            }
        });
    }

    private void fetchAndFilterRelatedPosts(List<String> userInterests, String viewedUid) {
        Set<String> interestSet = new HashSet<>();
        for (String tag : userInterests) {
            String t = tag.trim();
            if (!t.isEmpty()) interestSet.add(t.toLowerCase());
        }
        if (interestSet.isEmpty()) {
            _relatedPosts.postValue(new ArrayList<>());
            return;
        }

        String myUid = FirebaseManager.getCurrentUserId();
        postRepo.getActivePosts(new FirestorePostRepository.PostsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> posts) {
                List<Post> related = new ArrayList<>();
                for (Map<String, Object> p : posts) {
                    String authorUid = p.get("authorUid") != null ? p.get("authorUid").toString() : null;
                    if (myUid != null && myUid.equals(authorUid)) continue;
                    if (viewedUid != null && viewedUid.equals(authorUid)) continue;
                    String tag = p.get("interestTag") != null ? p.get("interestTag").toString().trim().toLowerCase() : "";
                    if (interestSet.contains(tag)) {
                        Post post = mapToPost(p);
                        if (post != null) related.add(post);
                    }
                }
                _relatedPosts.postValue(related);
            }

            @Override
            public void onError(String err) {
                _relatedPosts.postValue(new ArrayList<>());
            }
        });
    }

    public void updateProfile(String uid, Map<String, Object> data) {
        if (uid == null) return;
        userRepo.updateProfile(uid, data, new FirestoreUserRepository.ActionCallback() {
            @Override
            public void onSuccess(String msg) {
                _successMessage.postValue(msg);
                _actionStatus.postValue(true);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _actionStatus.postValue(false);
            }
        });
    }

    public void loadUserArchivedPosts(String userId) {
        if (userId == null) return;
        postRepo.getUserArchivedPosts(userId, new FirestorePostRepository.PostsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> posts) {
                List<Post> list = new ArrayList<>();
                for (Map<String, Object> p : posts) {
                    try {
                        String id = (String) p.get("id");
                        String name = (String) p.get("authorName");
                        String content = (String) p.get("content");
                        String tag = (String) p.get("interestTag");
                        String location = (String) p.get("location");
                        int max = p.get("maxMembers") instanceof Number ? ((Number)p.get("maxMembers")).intValue() : 10;
                        int cnt = p.get("memberCount") instanceof Number ? ((Number)p.get("memberCount")).intValue() : 1;
                        com.google.firebase.Timestamp endTs = (com.google.firebase.Timestamp) p.get("endTime");
                        long endMs = endTs != null ? endTs.toDate().getTime() : 0;
                        boolean archived = Boolean.TRUE.equals(p.get("archived"));
                        list.add(new Post(id, name, "", content, tag, location,
                                0, 0, cnt, 0, 0, max, false,
                                System.currentTimeMillis(), endMs, archived));
                    } catch (Exception ignored) {}
                }
                _archivedPosts.postValue(list);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _archivedPosts.postValue(new ArrayList<>());
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
            post.setAuthorUid(authorId);
            post.setAvatarUrl(authorAvatarUrl);
            if (imageUrl != null && !imageUrl.isEmpty()) post.setPostImageUri(imageUrl);
            return post;
        } catch (Exception e) {
            return null;
        }
    }
}
