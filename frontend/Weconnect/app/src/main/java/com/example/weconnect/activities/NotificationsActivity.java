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
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestoreNotificationRepository;
import com.example.weconnect.models.NotificationItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private TextView tvEmpty;
    private FirestoreNotificationRepository notifRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notifRepo = new FirestoreNotificationRepository();

        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmpty         = findViewById(R.id.tvNoNotifications);
        ImageView ivMark = findViewById(R.id.ivMarkAllRead);

        ivMark.setOnClickListener(v -> markAllAsRead());
        setupBottomNavigation();
        loadNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void loadNotifications() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) { showEmpty(); return; }

        notifRepo.getNotifications(uid, new FirestoreNotificationRepository.NotificationsCallback() {
            @Override public void onSuccess(List<Map<String, Object>> notifs) {
                // Convert Map → NotificationItem
                List<NotificationItem> items = new ArrayList<>();
                for (Map<String, Object> n : notifs) {
                    NotificationItem item = new NotificationItem();
                    item.setId(n.containsKey("id") ? String.valueOf(n.get("id")) : "");
                    item.setMessage(n.containsKey("message") ? (String) n.get("message") : "");
                    item.setType(n.containsKey("type") ? (String) n.get("type") : "");
                    item.setActorName(n.containsKey("actorName") ? (String) n.get("actorName") : "");
                    item.setRead(Boolean.TRUE.equals(n.get("read")));
                    com.google.firebase.Timestamp ts = (com.google.firebase.Timestamp) n.get("createdAt");
                    if (ts != null) item.setCreatedAt(ts.toDate().toString());
                    items.add(item);
                }
                runOnUiThread(() -> displayNotifications(items));
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void displayNotifications(List<NotificationItem> items) {
        if (items.isEmpty()) {
            showEmpty();
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
            List<Object> grouped = NotificationAdapter.groupByDate(items);
            adapter = new NotificationAdapter(this, grouped);
            rvNotifications.setLayoutManager(new LinearLayoutManager(this));
            rvNotifications.setAdapter(adapter);
        }
    }

    private void showEmpty() {
        tvEmpty.setVisibility(View.VISIBLE);
        rvNotifications.setVisibility(View.GONE);
    }

    private void markAllAsRead() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;

        notifRepo.markAllAsRead(uid, new FirestoreNotificationRepository.ActionCallback() {
            @Override public void onSuccess(String msg) {
                runOnUiThread(() -> {
                    if (adapter != null) adapter.markAllRead();
                    Toast.makeText(NotificationsActivity.this,
                        "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onError(String err) {
                Toast.makeText(NotificationsActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBottomNavigation() {
        FrameLayout btnHome     = findViewById(R.id.btnHomeNotif);
        FrameLayout btnMessages = findViewById(R.id.btnMessagesNotif);
        FrameLayout btnProfile  = findViewById(R.id.btnProfileNotif);

        if (btnHome != null) btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        if (btnMessages != null) btnMessages.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatListActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        if (btnProfile != null) btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("user_uid", FirebaseManager.getCurrentUserId());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
