package com.example.weconnect.presentation.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.data.repository.FirestoreNotificationRepository;
import com.example.weconnect.domain.model.NotificationItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NotificationViewModel extends AndroidViewModel {

    private final FirestoreNotificationRepository notifRepo;

    private final MutableLiveData<List<NotificationItem>> _notifications = new MutableLiveData<>();
    public LiveData<List<NotificationItem>> notifications = _notifications;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    public NotificationViewModel(@NonNull Application application) {
        super(application);
        notifRepo = new FirestoreNotificationRepository();
    }

    public void loadNotifications(String userId) {
        if (userId == null) return;
        notifRepo.getNotifications(userId, new FirestoreNotificationRepository.NotificationsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> notifs) {
                List<NotificationItem> result = new ArrayList<>();
                for (Map<String, Object> map : notifs) {
                    NotificationItem item = new NotificationItem();
                    item.setId((String) map.get("id"));
                    item.setType((String) map.get("type"));
                    item.setMessage((String) map.get("message"));
                    item.setActorName((String) map.get("actorName"));
                    item.setRelatedUserId((String) map.get("actorId"));
                    item.setRelatedPostId((String) map.get("postId"));
                    item.setRead(Boolean.TRUE.equals(map.get("read")));
                    
                    com.google.firebase.Timestamp ts = (com.google.firebase.Timestamp) map.get("createdAt");
                    if (ts != null) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());
                        item.setCreatedAt(sdf.format(ts.toDate()));
                    }
                    result.add(item);
                }
                _notifications.postValue(result);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _notifications.postValue(new ArrayList<>());
            }
        });
    }

    public void markAsRead(String notifId) {
        if (notifId == null) return;
        notifRepo.markAsRead(notifId, new FirestoreNotificationRepository.ActionCallback() {
            @Override
            public void onSuccess(String msg) {
                // Đã xử lý
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
            }
        });
    }

    public void markAllAsRead(String userId) {
        if (userId == null) return;
        notifRepo.markAllAsRead(userId, new FirestoreNotificationRepository.ActionCallback() {
            @Override
            public void onSuccess(String msg) {
                // reload or notify
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
            }
        });
    }
}
