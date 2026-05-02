package com.example.weconnect.presentation.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.R;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestorePostRepository;
import com.example.weconnect.data.repository.FirestoreUserRepository;
import com.example.weconnect.domain.model.Post;
import com.example.weconnect.domain.model.SearchResultItem;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchViewModel extends AndroidViewModel {

    private final FirestorePostRepository postRepo;
    private final FirestoreUserRepository userRepo;

    private final MutableLiveData<List<SearchResultItem>> _searchResults = new MutableLiveData<>();
    public LiveData<List<SearchResultItem>> searchResults = _searchResults;

    private List<SearchResultItem> userResults = new ArrayList<>();
    private List<SearchResultItem> postResults = new ArrayList<>();
    private boolean usersLoaded, postsLoaded;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        postRepo = new FirestorePostRepository();
        userRepo = new FirestoreUserRepository();
    }

    public void performSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            _searchResults.postValue(new ArrayList<>());
            return;
        }

        userResults.clear();
        postResults.clear();
        usersLoaded = false;
        postsLoaded = false;

        String currentUid = FirebaseManager.getCurrentUserId();

        // Tìm users
        userRepo.searchUsers(keyword.trim(), currentUid != null ? currentUid : "",
            new FirestoreUserRepository.UsersCallback() {
                @Override public void onSuccess(List<Map<String, Object>> users) {
                    if (!users.isEmpty()) {
                        userResults.add(new SearchResultItem(
                            SearchResultItem.TYPE_SECTION, "Người dùng", "", 0));
                        for (Map<String, Object> u : users) {
                            String name = u.get("fullName") != null ? u.get("fullName").toString() : "";
                            String uid  = u.get("id") != null ? u.get("id").toString() : "";
                            SearchResultItem item = new SearchResultItem(
                                SearchResultItem.TYPE_USER, name, "", R.drawable.ic_user_placeholder);
                            item.setUserUid(uid);
                            userResults.add(item);
                        }
                    }
                    usersLoaded = true;
                    mergeResults();
                }
                @Override public void onError(String err) {
                    usersLoaded = true;
                    mergeResults();
                }
            });

        // Tìm posts
        postRepo.searchPosts(keyword.trim(), new FirestorePostRepository.PostsCallback() {
            @Override public void onSuccess(List<Map<String, Object>> posts) {
                if (!posts.isEmpty()) {
                    postResults.add(new SearchResultItem(
                        SearchResultItem.TYPE_SECTION, "Bài viết", "", 0));
                    for (Map<String, Object> p : posts) {
                        String content  = (String) p.get("content");
                        String location = (String) p.get("location");
                        String authorName = (String) p.get("authorName");
                        String tag = (String) p.get("interestTag");
                        int max = p.get("maxMembers") instanceof Number ? ((Number)p.get("maxMembers")).intValue() : 10;
                        int cnt = p.get("memberCount") instanceof Number ? ((Number)p.get("memberCount")).intValue() : 1;
                        Timestamp endTs = (Timestamp) p.get("endTime");
                        long endMs = endTs != null ? endTs.toDate().getTime() : 0;
                        String postId = (String) p.get("id");

                        String subtitle = location != null && !location.isEmpty() ?
                            "📍 " + location : (authorName != null ? authorName : "");

                        Post postObj = new Post(postId, authorName, "", content, tag, location,
                            0, 0, cnt, 0, 0, max, false, System.currentTimeMillis(), endMs, false);

                        postResults.add(new SearchResultItem(
                            SearchResultItem.TYPE_POST, content, subtitle, 0,
                            authorName, content, tag, location, cnt, max, postObj));
                    }
                }
                postsLoaded = true;
                mergeResults();
            }
            @Override public void onError(String err) {
                postsLoaded = true;
                mergeResults();
            }
        });
    }

    private synchronized void mergeResults() {
        if (!usersLoaded || !postsLoaded) return;
        List<SearchResultItem> merged = new ArrayList<>();
        merged.addAll(userResults);
        merged.addAll(postResults);
        _searchResults.postValue(merged);
    }
}
