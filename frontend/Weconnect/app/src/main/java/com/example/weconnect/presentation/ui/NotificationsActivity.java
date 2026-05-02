package com.example.weconnect.presentation.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.weconnect.R;
import com.example.weconnect.presentation.adapter.NotificationAdapter;
import com.example.weconnect.databinding.ActivityNotificationsBinding;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.domain.model.NotificationItem;
import com.example.weconnect.presentation.viewmodel.NotificationViewModel;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private ActivityNotificationsBinding binding;
    private NotificationViewModel notifViewModel;
    private NotificationAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        notifViewModel = new ViewModelProvider(this).get(NotificationViewModel.class);

        binding.ivMarkAllRead.setOnClickListener(v -> markAllAsRead());
        setupBottomNavigation();
        setupObservers();
        loadNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void loadNotifications() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) { 
            showEmpty(); 
            return; 
        }
        notifViewModel.loadNotifications(uid);
    }

    private void setupObservers() {
        notifViewModel.notifications.observe(this, notifs -> {
            if (notifs != null && !notifs.isEmpty()) {
                displayNotifications(notifs);
            } else {
                showEmpty();
            }
        });

        notifViewModel.error.observe(this, err -> {
            if (err != null && !err.isEmpty()) {
                Toast.makeText(this, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayNotifications(List<NotificationItem> items) {
        binding.tvNoNotifications.setVisibility(View.GONE);
        binding.rvNotifications.setVisibility(View.VISIBLE);
        List<Object> grouped = NotificationAdapter.groupByDate(items);
        adapter = new NotificationAdapter(this, grouped);
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);
    }

    private void showEmpty() {
        binding.tvNoNotifications.setVisibility(View.VISIBLE);
        binding.rvNotifications.setVisibility(View.GONE);
    }

    private void markAllAsRead() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) return;

        notifViewModel.markAllAsRead(uid);
        if (adapter != null) adapter.markAllRead();
        Toast.makeText(NotificationsActivity.this, "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
    }

    private void setupBottomNavigation() {
        if (binding.btnHomeNotif != null) binding.btnHomeNotif.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        if (binding.btnMessagesNotif != null) binding.btnMessagesNotif.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatListActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        if (binding.btnProfileNotif != null) binding.btnProfileNotif.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("user_uid", FirebaseManager.getCurrentUserId());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
