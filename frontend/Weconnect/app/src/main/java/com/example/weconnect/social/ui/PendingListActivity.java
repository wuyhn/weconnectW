package com.example.weconnect.social.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.social.ui.PendingRequestAdapter;
import com.example.weconnect.post.data.PostApiService;
import com.example.weconnect.core.data.RetrofitClient;
import com.example.weconnect.core.data.ApiResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PendingListActivity extends AppCompatActivity
        implements PendingRequestAdapter.OnMemberActionListener {

    private RecyclerView rvPendingRequests;
    private TextView tvNoPending;
    private long postId;
    private List<Map<String, Object>> pendingMembers = new ArrayList<>();
    private PendingRequestAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_list);

        postId = getIntent().getLongExtra("post_id", -1);

        ImageView ivBack = findViewById(R.id.ivBackPending);
        rvPendingRequests = findViewById(R.id.rvPendingRequests);
        tvNoPending = findViewById(R.id.tvNoPending);

        ivBack.setOnClickListener(v -> finish());

        loadPendingMembers();
    }

    private void loadPendingMembers() {
        if (postId <= 0) {
            tvNoPending.setVisibility(View.VISIBLE);
            rvPendingRequests.setVisibility(View.GONE);
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
                    pendingMembers = response.body().getResult();

                    // Enrich with user names
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

        // Try to load user names for each pending member
        com.example.weconnect.api.UserApiService userApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);

        // For simplicity, display with user IDs first, then enrich as responses come
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
                        String fullName = profile.get("fullName") != null
                                ? profile.get("fullName").toString() : "Người dùng #" + userId;
                        pendingMembers.get(index).put("userName", fullName);
                        if (adapter != null) {
                            adapter.notifyItemChanged(index);
                        }
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                    // Keep default name
                }
            });
        }
    }

    private void displayPendingList() {
        tvNoPending.setVisibility(View.GONE);
        rvPendingRequests.setVisibility(View.VISIBLE);

        adapter = new PendingRequestAdapter(this, pendingMembers, postId, this);
        rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));
        rvPendingRequests.setAdapter(adapter);
    }

    private void showEmpty() {
        tvNoPending.setVisibility(View.VISIBLE);
        rvPendingRequests.setVisibility(View.GONE);
    }

    @Override
    public void onApproved(int position) {
        // Could refresh list or update count
    }

    @Override
    public void onRejected(int position) {
        // Could refresh list or update count
    }
}
