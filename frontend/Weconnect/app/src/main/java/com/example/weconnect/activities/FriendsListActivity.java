package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.FriendsListAdapter;
import com.example.weconnect.api.FriendApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendsListActivity extends AppCompatActivity {

    private RecyclerView rvFriendsList;
    private View layoutEmpty;
    private TextView tvEmpty;
    private ProgressBar pbLoading;
    private FriendApiService friendApi;
    private FriendsListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends_list);

        rvFriendsList = findViewById(R.id.rvFriendsList);
        layoutEmpty = findViewById(R.id.layoutFriendsEmpty);
        tvEmpty = findViewById(R.id.tvFriendsEmpty);
        pbLoading = findViewById(R.id.pbFriendsLoading);

        findViewById(R.id.ivBackFriendsList).setOnClickListener(v -> finish());

        rvFriendsList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FriendsListAdapter(null, this::onFriendClick);
        rvFriendsList.setAdapter(adapter);

        RetrofitClient.loadToken(this);
        friendApi = RetrofitClient.getClient().create(FriendApiService.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFriends();
    }

    private void loadFriends() {
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        rvFriendsList.setVisibility(View.GONE);

        friendApi.getFriends().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                pbLoading.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null
                        && !response.body().getResult().isEmpty()) {
                    adapter.update(response.body().getResult());
                    rvFriendsList.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setText("Chưa có bạn bè");
                    layoutEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                pbLoading.setVisibility(View.GONE);
                tvEmpty.setText("Không thể tải danh sách bạn bè");
                layoutEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(FriendsListActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onFriendClick(Map<String, Object> friend) {
        long friendId = -1;
        try {
            if (friend.get("id") != null)
                friendId = ((Number) friend.get("id")).longValue();
        } catch (Exception ignored) {}

        if (friendId <= 0) {
            Toast.makeText(this, "Người dùng không khả dụng", Toast.LENGTH_SHORT).show();
            return;
        }

        String friendName = friend.get("fullName") != null
                ? friend.get("fullName").toString() : "";

        // Block check được xử lý bên trong UserProfileActivity khi gọi getUserProfile
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra("user_id", friendId);
        intent.putExtra("username", friendName);
        intent.putExtra("view_other", true);
        startActivity(intent);
    }
}
