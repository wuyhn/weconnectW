package com.example.weconnect.notification.data;

import java.util.ArrayList;
import java.util.List;

public class FakeNotificationRepository {

    public enum NotificationType {
        FRIEND_REQUEST_RECEIVED,  // Nhận lời mời kết bạn
        FRIEND_REQUEST_SENT,      // Đã gửi lời mời
        FRIEND_ACCEPTED,          // Đã chấp nhận kết bạn
        JOIN_REQUEST,             // Yêu cầu tham gia hoạt động
        JOIN_APPROVED,            // Đã được duyệt tham gia
        GENERAL                   // Thông báo chung
    }

    public static class NotificationItem {
        private final NotificationType type;
        private final String message;
        private final String relatedUsername;
        private final long timestamp;
        private boolean isRead;
        private boolean isActioned; // Đã xử lý (chấp nhận/từ chối)

        public NotificationItem(NotificationType type, String message, String relatedUsername, long timestamp) {
            this.type = type;
            this.message = message;
            this.relatedUsername = relatedUsername;
            this.timestamp = timestamp;
            this.isRead = false;
            this.isActioned = false;
        }

        public NotificationType getType() { return type; }
        public String getMessage() { return message; }
        public String getRelatedUsername() { return relatedUsername; }
        public long getTimestamp() { return timestamp; }
        public boolean isRead() { return isRead; }
        public void setRead(boolean read) { isRead = read; }
        public boolean isActioned() { return isActioned; }
        public void setActioned(boolean actioned) { isActioned = actioned; }
    }

    private static FakeNotificationRepository instance;
    private final List<NotificationItem> notifications = new ArrayList<>();

    private FakeNotificationRepository() {
        seed();
    }

    public static synchronized FakeNotificationRepository getInstance() {
        if (instance == null) {
            instance = new FakeNotificationRepository();
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    public List<NotificationItem> getNotifications() {
        return new ArrayList<>(notifications);
    }

    public void addNotification(NotificationItem item) {
        notifications.add(0, item);
    }

    public int getUnreadCount() {
        int count = 0;
        for (NotificationItem item : notifications) {
            if (!item.isRead()) count++;
        }
        return count;
    }

    private void seed() {
        // Không seed fake data - chỉ dùng dữ liệu thật từ backend
    }
}
