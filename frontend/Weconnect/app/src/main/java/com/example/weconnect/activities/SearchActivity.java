package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.SearchResultAdapter;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestorePostRepository;
import com.example.weconnect.api.FirestoreUserRepository;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.SearchResultItem;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchActivity extends AppCompatActivity {

    private TextInputEditText etSearch;
    private SearchResultAdapter searchResultAdapter;
    private FirestorePostRepository postRepo;
    private FirestoreUserRepository userRepo;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private List<SearchResultItem> userResults = new ArrayList<>();
    private List<SearchResultItem> postResults = new ArrayList<>();
    private boolean usersLoaded, postsLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        postRepo = new FirestorePostRepository();
        userRepo = new FirestoreUserRepository();

        ImageView ivBack   = findViewById(R.id.ivBackSearch);
        etSearch           = findViewById(R.id.etSearch);
        RecyclerView rv    = findViewById(R.id.rvSearchResults);

        searchResultAdapter = new SearchResultAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(searchResultAdapter);

        ivBack.setOnClickListener(v -> finish());

        // Auto focus
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        etSearch.requestFocus();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> performSearch(s.toString());
                searchHandler.postDelayed(searchRunnable, 300);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                performSearch(etSearch.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void performSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            searchResultAdapter.submitList(new ArrayList<>());
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

    private void mergeResults() {
        if (!usersLoaded || !postsLoaded) return;
        List<SearchResultItem> merged = new ArrayList<>();
        merged.addAll(userResults);
        merged.addAll(postResults);
        runOnUiThread(() -> searchResultAdapter.submitList(merged));
    }
}
