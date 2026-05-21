package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.PendingRequestAdapter;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.PostResponse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PendingListActivity extends AppCompatActivity
        implements PendingRequestAdapter.OnMemberActionListener {

    private RecyclerView rvPendingRequests;
    private TextView tvNoPending;
    private TextView tvTitle;
    private long postId;
    private List<Map<String, Object>> pendingMembers = new ArrayList<>();
    private PendingRequestAdapter adapter;

    private PostResponse currentPost;
    private int currentMemberCount = 0;
    private int maxMemberCount = 0;
    private View cardActivityDetail;
    private TextView tvCardActivityTag;
    private TextView tvCardActivityDate;
    private TextView tvCardActivityMembers;
    private TextView btnViewPostDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_list);

        postId = getIntent().getLongExtra("post_id", -1);

        ImageView ivBack = findViewById(R.id.ivBackPending);
        tvTitle = findViewById(R.id.tvPendingTitle);
        rvPendingRequests = findViewById(R.id.rvPendingRequests);
        tvNoPending = findViewById(R.id.tvNoPending);

        cardActivityDetail = findViewById(R.id.cardActivityDetail);
        tvCardActivityTag = findViewById(R.id.tvCardActivityTag);
        tvCardActivityDate = findViewById(R.id.tvCardActivityDate);
        tvCardActivityMembers = findViewById(R.id.tvCardActivityMembers);
        btnViewPostDetail = findViewById(R.id.btnViewPostDetail);

        ivBack.setOnClickListener(v -> finish());
        btnViewPostDetail.setOnClickListener(v -> openPostDetail());

        applyWindowInsets();
        loadPostDetails();
        loadPendingMembers();
    }

    private void applyWindowInsets() {
        View header = findViewById(R.id.headerPending);
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBarHeight + dpToPx(14),
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(rvPendingRequests, (v, insets) -> {
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), navBarHeight + dpToPx(16));
            return insets;
        });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void loadPostDetails() {
        if (postId <= 0) return;
        RetrofitClient.loadToken(this);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);
        postApi.getPost(postId).enqueue(new Callback<ApiResponse<PostResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PostResponse>> call,
                                   Response<ApiResponse<PostResponse>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    currentPost = response.body().getResult();
                    updateTitle(currentPost);
                    updateActivityCard(currentPost);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PostResponse>> call, Throwable t) {}
        });
    }

    private void updateTitle(PostResponse post) {
        String tag = post.getInterestTag();
        if (tag == null) tag = "";

        String startDate = formatDate(post.getStartTime());
        String endDate = formatDate(post.getEndTime());

        String title;
        if (!tag.isEmpty() && !startDate.isEmpty()) {
            if (!endDate.isEmpty() && !endDate.equals(startDate)) {
                title = "Yêu cầu tham gia: " + tag + " - " + startDate + " - " + endDate;
            } else {
                title = "Yêu cầu tham gia: " + tag + " - " + startDate;
            }
        } else if (!tag.isEmpty()) {
            title = "Yêu cầu tham gia: " + tag;
        } else if (!startDate.isEmpty()) {
            title = "Yêu cầu tham gia: " + startDate;
        } else {
            title = "Yêu cầu tham gia";
        }

        if (tvTitle != null) tvTitle.setText(title);
    }

    private void updateActivityCard(PostResponse post) {
        if (post == null) return;

        String tag = post.getInterestTag();
        tvCardActivityTag.setText(tag != null && !tag.isEmpty() ? tag : "Hoạt động");

        String startDate = formatDate(post.getStartTime());
        String endDate = formatDate(post.getEndTime());
        String dateText;
        if (!startDate.isEmpty() && !endDate.isEmpty() && !endDate.equals(startDate)) {
            dateText = startDate + " - " + endDate;
        } else if (!startDate.isEmpty()) {
            dateText = startDate;
        } else {
            dateText = "";
        }

        String timeStr = formatTime(post.getStartTime());
        if (!timeStr.isEmpty() && !dateText.isEmpty()) {
            dateText = dateText + "  " + timeStr;
        }

        tvCardActivityDate.setText(dateText);
        tvCardActivityDate.setVisibility(dateText.isEmpty() ? View.GONE : View.VISIBLE);

        currentMemberCount = post.getMemberCount();
        maxMemberCount = post.getMaxMembers();
        updateMemberCountCard();

        cardActivityDetail.setVisibility(View.VISIBLE);

        if (adapter != null) adapter.updateMemberCounts(currentMemberCount, maxMemberCount);
    }

    private void updateMemberCountCard() {
        if (tvCardActivityMembers == null) return;
        if (maxMemberCount > 0) {
            tvCardActivityMembers.setText(currentMemberCount + "/" + maxMemberCount + " thành viên");
        } else {
            tvCardActivityMembers.setText(currentMemberCount + " thành viên");
        }
    }

    private void openPostDetail() {
        if (currentPost == null) {
            Toast.makeText(this, "Hoạt động không khả dụng", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentPost.isCancelled() || currentPost.isArchived() || currentPost.isExpired()) {
            Toast.makeText(this, "Hoạt động không khả dụng", Toast.LENGTH_SHORT).show();
            return;
        }
        Post post = currentPost.toPost();
        Intent intent = new Intent(this, PostDetailActivity.class);
        intent.putExtra("post", post);
        startActivity(intent);
    }

    private String formatDate(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return "";
        try {
            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date d = isoFmt.parse(isoTime);
            if (d == null) return "";
            SimpleDateFormat displayFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return displayFmt.format(d);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return "";
        try {
            SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date d = isoFmt.parse(isoTime);
            if (d == null) return "";
            SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return timeFmt.format(d);
        } catch (Exception e) {
            return "";
        }
    }

    private void loadPendingMembers() {
        if (postId <= 0) {
            showEmpty();
            return;
        }

        RetrofitClient.loadToken(this);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);

        postApi.getPendingMembers(postId).enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    pendingMembers = new ArrayList<>(response.body().getResult());
                    enrichAndDisplay();
                } else {
                    showEmpty();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                Toast.makeText(PendingListActivity.this,
                        "Lỗi kết nối. Thử lại sau.", Toast.LENGTH_SHORT).show();
                showEmpty();
            }
        });
    }

    private void enrichAndDisplay() {
        if (pendingMembers.isEmpty()) {
            showEmpty();
            return;
        }

        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);

        displayPendingList();

        for (int i = 0; i < pendingMembers.size(); i++) {
            Map<String, Object> member = pendingMembers.get(i);
            long userId = member.get("userId") != null
                    ? ((Number) member.get("userId")).longValue() : 0;
            if (userId <= 0) continue;

            int index = i;
            userApi.getUserProfile(userId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                       Response<ApiResponse<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getResult() != null) {
                        Map<String, Object> profile = response.body().getResult();
                        if (index < pendingMembers.size()) {
                            Map<String, Object> m = pendingMembers.get(index);
                            m.put("userName", profile.get("fullName") != null
                                    ? profile.get("fullName").toString() : "Người dùng #" + userId);
                            m.put("avatarUrl", profile.get("avatarUrl") != null
                                    ? profile.get("avatarUrl").toString() : "");
                            m.put("reputationScore", profile.get("reputationScore") != null
                                    ? ((Number) profile.get("reputationScore")).doubleValue() : 100.0);
                            m.put("averageRating", profile.get("averageRating") != null
                                    ? ((Number) profile.get("averageRating")).floatValue() : 0f);
                            m.put("totalReviewCount", profile.get("totalReviewCount") != null
                                    ? ((Number) profile.get("totalReviewCount")).intValue() : 0);
                            m.put("isActivityJoinLocked",
                                    Boolean.TRUE.equals(profile.get("isActivityJoinLocked")));
                            if (adapter != null) adapter.notifyItemChanged(index);
                        }
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {}
            });
        }
    }

    private void displayPendingList() {
        tvNoPending.setVisibility(View.GONE);
        rvPendingRequests.setVisibility(View.VISIBLE);

        adapter = new PendingRequestAdapter(this, pendingMembers, postId, this);
        adapter.updateMemberCounts(currentMemberCount, maxMemberCount);
        rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));
        rvPendingRequests.setAdapter(adapter);
    }

    private void showEmpty() {
        tvNoPending.setVisibility(View.VISIBLE);
        rvPendingRequests.setVisibility(View.GONE);
    }

    @Override
    public void onApproved(int adapterPosition) {
        removeItemAt(adapterPosition);
        if (maxMemberCount <= 0 || currentMemberCount < maxMemberCount) {
            currentMemberCount++;
        }
        if (maxMemberCount > 0) {
            currentMemberCount = Math.min(currentMemberCount, maxMemberCount);
        }
        updateMemberCountCard();
        if (adapter != null) adapter.updateMemberCounts(currentMemberCount, maxMemberCount);
    }

    @Override
    public void onRejected(int adapterPosition) {
        removeItemAt(adapterPosition);
    }

    private void removeItemAt(int position) {
        if (position < 0 || position >= pendingMembers.size()) return;
        pendingMembers.remove(position);
        if (adapter != null) adapter.notifyItemRemoved(position);
        if (pendingMembers.isEmpty()) showEmpty();
    }
}
