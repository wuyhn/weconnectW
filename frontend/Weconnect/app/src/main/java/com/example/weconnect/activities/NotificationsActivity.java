package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.adapters.NotificationAdapter;
import com.example.weconnect.api.NotificationApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.NotificationItem;
import com.example.weconnect.util.BadgeManager;
import com.example.weconnect.websocket.WebSocketManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private TextView tvEmpty;
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmpty = findViewById(R.id.tvNoNotifications);
        bottomNav = findViewById(R.id.footerNavNotif);
        ImageView ivMarkAllRead = findViewById(R.id.ivMarkAllRead);

        loadNotifications();

        ivMarkAllRead.setOnClickListener(v -> markAllAsRead());

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyNavBadge();
        loadNotifications();
        // Lắng nghe notification mới qua STOMP để auto-refresh
        if (WebSocketManager.getInstance().isConnected()) {
            WebSocketManager.getInstance().subscribeToNotifications(payload -> {
                loadNotifications();
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebSocketManager.getInstance().unsubscribeFromNotifications();
    }

    private void loadNotifications() {
        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();

        if (token == null) {
            showEmptyNotifications();
            return;
        }

        NotificationApiService apiService = RetrofitClient.getClient()
                .create(NotificationApiService.class);

        apiService.getNotifications().enqueue(new Callback<ApiResponse<List<NotificationItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<NotificationItem>>> call,
                                   Response<ApiResponse<List<NotificationItem>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    displayNotifications(response.body().getResult());
                } else {
                    showEmptyNotifications();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<NotificationItem>>> call, Throwable t) {
                showEmptyNotifications();
                Toast.makeText(NotificationsActivity.this,
                        "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayNotifications(List<NotificationItem> notifications) {
        // Sync badge với số unread thực tế từ server
        int unreadCount = 0;
        for (NotificationItem n : notifications) {
            if (!n.isRead()) unreadCount++;
        }
        BadgeManager.setCount(unreadCount);
        applyNavBadge();

        if (notifications.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);

            List<Object> groupedItems = NotificationAdapter.groupByDate(notifications);
            adapter = new NotificationAdapter(this, groupedItems,
                    () -> applyNavBadge());
            rvNotifications.setLayoutManager(new LinearLayoutManager(this));
            rvNotifications.setAdapter(adapter);
        }
    }

    private void showEmptyNotifications() {
        tvEmpty.setVisibility(View.VISIBLE);
        rvNotifications.setVisibility(View.GONE);
        BadgeManager.reset();
        applyNavBadge();
    }

    private void markAllAsRead() {
        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();

        if (token != null) {
            NotificationApiService apiService = RetrofitClient.getClient()
                    .create(NotificationApiService.class);

            apiService.markAllAsRead().enqueue(new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(Call<ApiResponse<Void>> call,
                                       Response<ApiResponse<Void>> response) {
                    // Reset badge chỉ khi markAllAsRead thành công
                    BadgeManager.reset();
                    applyNavBadge();
                    if (adapter != null) {
                        adapter.markAllRead();
                    }
                    Toast.makeText(NotificationsActivity.this,
                            "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                    Toast.makeText(NotificationsActivity.this,
                            "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        BadgeManager.reset();
        applyNavBadge();
        if (adapter != null) {
            adapter.markAllRead();
        }
        Toast.makeText(this, "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
    }

    private void applyNavBadge() {
        if (bottomNav == null) return;
        int count = BadgeManager.getCount();
        if (count > 0) {
            com.google.android.material.badge.BadgeDrawable badge =
                    bottomNav.getOrCreateBadge(R.id.nav_notifications);
            badge.setVisible(true);
            badge.setMaxCharacterCount(3);
            badge.setNumber(count);
        } else {
            bottomNav.removeBadge(R.id.nav_notifications);
        }
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        bottomNav.setSelectedItemId(R.id.nav_notifications);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_messages) {
                Intent intent = new Intent(this, ChatListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_notifications) {
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra("username", RetrofitClient.getUserName(this));
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
}
