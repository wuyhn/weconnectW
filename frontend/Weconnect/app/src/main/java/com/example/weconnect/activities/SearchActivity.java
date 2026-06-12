package com.example.weconnect.activities;

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
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.PostResponse;
import com.example.weconnect.models.SearchResultItem;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchActivity extends AppCompatActivity {

    private ImageView ivBackSearch;
    private TextInputEditText etSearch;
    private RecyclerView rvSearchResults;

    private SearchResultAdapter searchResultAdapter;
    private PostApiService postApiService;
    private UserApiService userApiService;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Kết quả tạm lưu để merge
    private List<SearchResultItem> userResults = new ArrayList<>();
    private List<SearchResultItem> postResults = new ArrayList<>();
    private boolean usersLoaded, postsLoaded;
    private int searchRequestVersion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        initViews();
        setupRecyclerView();
        setupApi();
        setupClickListeners();
        setupSearchListener();
        autoFocusSearch();
    }

    private void initViews() {
        ivBackSearch = findViewById(R.id.ivBackSearch);
        etSearch = findViewById(R.id.etSearch);
        rvSearchResults = findViewById(R.id.rvSearchResults);
    }

    private void setupRecyclerView() {
        searchResultAdapter = new SearchResultAdapter(this);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(searchResultAdapter);
    }

    private void setupApi() {
        RetrofitClient.loadToken(this);
        postApiService = RetrofitClient.getClient().create(PostApiService.class);
        userApiService = RetrofitClient.getClient().create(UserApiService.class);
    }

    private void setupClickListeners() {
        ivBackSearch.setOnClickListener(v -> finish());
    }

    private void autoFocusSearch() {
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        etSearch.requestFocus();
    }

    private void setupSearchListener() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Debounce 300ms
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    Editable editable = etSearch.getText();
                    String keyword = editable != null ? editable.toString().trim() : "";
                    performSearch(keyword);
                };
                searchHandler.postDelayed(searchRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                Editable editable = etSearch.getText();
                String keyword = editable != null ? editable.toString().trim() : "";
                performSearch(keyword);
                return true;
            }
            return false;
        });
    }

    private void performSearch(String keyword) {
        String trimmedKeyword = keyword != null ? keyword.trim() : "";
        int requestVersion = ++searchRequestVersion;

        if (trimmedKeyword.isEmpty()) {
            userResults.clear();
            postResults.clear();
            searchResultAdapter.clearData();
            return;
        }

        // Xóa kết quả cũ ngay khi bắt đầu search mới để RecyclerView không hiển thị dữ liệu cũ.
        searchResultAdapter.clearData();
        userResults.clear();
        postResults.clear();
        usersLoaded = false;
        postsLoaded = false;

        // Search users partial match
        userApiService.searchUsersPartial(trimmedKeyword).enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (requestVersion != searchRequestVersion) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    List<Map<String, Object>> users = response.body().getResult();
                    if (!users.isEmpty()) {
                        userResults.add(new SearchResultItem(
                                SearchResultItem.TYPE_SECTION, "Người dùng", "", 0));
                        for (Map<String, Object> u : users) {
                            // Bỏ qua users đã chặn mình (với họ, tôi không tồn tại và ngược lại)
                            if (asBoolean(u.get("hasBlockedMe"))) continue;
                            String fullName = u.get("fullName") != null ? u.get("fullName").toString() : "";
                            long userId = u.get("id") != null ? ((Number) u.get("id")).longValue() : 0;
                            String avatarUrl = u.get("avatarUrl") != null ? u.get("avatarUrl").toString() : "";
                            SearchResultItem item = new SearchResultItem(
                                    SearchResultItem.TYPE_USER, fullName, "", R.drawable.ic_user_placeholder);
                            item.setUserId(userId);
                            item.setAvatarUrl(avatarUrl);
                            item.setBlockedByMe(asBoolean(u.get("isBlockedByMe")));
                            item.setHasBlockedMe(asBoolean(u.get("hasBlockedMe")));
                            item.setBlockedBetweenUsers(asBoolean(u.get("isBlockedBetweenUsers")));
                            userResults.add(item);
                        }
                    }
                }
                usersLoaded = true;
                mergeResults();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (requestVersion != searchRequestVersion) return;
                usersLoaded = true;
                mergeResults();
            }
        });

        // Search posts: chỉ truyền keyword đã trim vào @Query("keyword"), không gọi endpoint lấy toàn bộ bài viết.
        postApiService.searchPosts(trimmedKeyword).enqueue(new Callback<ApiResponse<List<PostResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PostResponse>>> call,
                                   Response<ApiResponse<List<PostResponse>>> response) {
                if (requestVersion != searchRequestVersion) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    List<PostResponse> posts = response.body().getResult();
                    if (!posts.isEmpty()) {
                        postResults.add(new SearchResultItem(
                                SearchResultItem.TYPE_SECTION, "Bài viết", "", 0));
                        for (PostResponse pr : posts) {
                            Post post = pr.toPost();
                            String subtitle;
                            if (post.getLocation() != null && post.getLocation().length() > 0) {
                                subtitle = "📍 " + post.getLocation();
                            } else if (post.getUsername() != null && post.getUsername().length() > 0) {
                                subtitle = post.getUsername();
                            } else {
                                subtitle = "";
                            }
                            postResults.add(new SearchResultItem(
                                    SearchResultItem.TYPE_POST,
                                    post.getContent(), subtitle, 0,
                                    post.getUsername(), post.getContent(),
                                    post.getInterestTag(), post.getLocation(),
                                    post.getMemberCount(), post.getMaxMembers(), post));
                        }
                    }
                }
                postsLoaded = true;
                mergeResults();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PostResponse>>> call, Throwable t) {
                if (requestVersion != searchRequestVersion) return;
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
        if (merged.isEmpty()) {
            merged.add(new SearchResultItem(SearchResultItem.TYPE_EMPTY, "", "", 0));
        }
        searchResultAdapter.submitList(merged);
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }
}
