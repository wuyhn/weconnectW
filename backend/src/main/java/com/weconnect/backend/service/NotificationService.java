package com.weconnect.backend.service;

import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // Lấy danh sách thông báo
    public List<Notification> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // Tạo thông báo (basic)
    @Transactional
    public void createNotification(Long userId, Notification.NotificationType type,
                                   String message, String relatedUsername) {
        createNotification(userId, type, message, relatedUsername, null, null);
    }

    // Tạo thông báo (với context post/user)
    @Transactional
    public void createNotification(Long userId, Notification.NotificationType type,
                                   String message, String relatedUsername,
                                   Long relatedPostId, Long relatedUserId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .relatedUsername(relatedUsername)
                .relatedPostId(relatedPostId)
                .relatedUserId(relatedUserId)
                .isRead(false)
                .isActioned(false)
                .build();
        notificationRepository.save(notification);
    }

    // Đánh dấu đã đọc
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo."));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    // Đánh dấu đã xử lý
    public void markAsActioned(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo."));
        notification.setActioned(true);
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    // Đọc tất cả
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    // Số chưa đọc
    public int getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }
}
