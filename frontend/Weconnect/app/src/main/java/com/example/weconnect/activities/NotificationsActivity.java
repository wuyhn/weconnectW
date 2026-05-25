package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
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
import com.example.weconnect.data.FakeNotificationRepository;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.NotificationItem;
import com.example.weconnect.util.BadgeManager;
import com.example.weconnect.websocket.WebSocketManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private TextView tvEmpty;
    private TextView tvNotifBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmpty = findViewById(R.id.tvNoNotifications);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);
        ImageView ivMarkAllRead = findViewById(R.id.ivMarkAllRead);

        // Hiển thị badge theo count hiện tại (không reset ở đây)
        BadgeManager.applyBadge(tvNotifBadge);

        loadNotifications();

        ivMarkAllRead.setOnClickListener(v -> markAllAsRead());

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BadgeManager.applyBadge(tvNotifBadge);
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
            loadFakeNotifications();
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
                    loadFakeNotifications();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<NotificationItem>>> call, Throwable t) {
                loadFakeNotifications();
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
        BadgeManager.applyBadge(tvNotifBadge);

        if (notifications.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);

            List<Object> groupedItems = NotificationAdapter.groupByDate(notifications);
            adapter = new NotificationAdapter(this, groupedItems,
                    () -> BadgeManager.applyBadge(tvNotifBadge));
            rvNotifications.setLayoutManager(new LinearLayoutManager(this));
            rvNotifications.setAdapter(adapter);
        }
    }

    private void loadFakeNotifications() {
        List<FakeNotificationRepository.NotificationItem> fakeNotifs =
                FakeNotificationRepository.getInstance().getNotifications();

        if (fakeNotifs.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);

            List<Object> groupedItems = com.example.weconnect.adapters.NotificationAdapter
                    .groupByDateFake(fakeNotifs);
            adapter = new NotificationAdapter(this, groupedItems,
                    () -> BadgeManager.applyBadge(tvNotifBadge));
            rvNotifications.setLayoutManager(new LinearLayoutManager(this));
            rvNotifications.setAdapter(adapter);
        }
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
                    BadgeManager.applyBadge(tvNotifBadge);
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
        } else {
            // Fake mark all read
            for (FakeNotificationRepository.NotificationItem item :
                    FakeNotificationRepository.getInstance().getNotifications()) {
                item.setRead(true);
            }
            BadgeManager.reset();
            BadgeManager.applyBadge(tvNotifBadge);
            if (adapter != null) {
                adapter.markAllRead();
            }
            Toast.makeText(this, "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBottomNavigation() {
        FrameLayout btnHome = findViewById(R.id.btnHomeNotif);
        FrameLayout btnMessages = findViewById(R.id.btnMessagesNotif);
        FrameLayout btnProfile = findViewById(R.id.btnProfileNotif);

        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
        if (btnMessages != null) {
            btnMessages.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChatListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> {
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra("username", RetrofitClient.getUserName(this));
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }
}
