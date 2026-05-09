package com.weconnect.backend.service;

import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.repository.NotificationRepository;
import com.weconnect.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private FCMService fcmService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public List<Notification> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void createNotification(Long userId, Notification.NotificationType type,
                                   String message, String relatedUsername) {
        createNotification(userId, type, message, relatedUsername, null, null);
    }

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
        Notification saved = notificationRepository.save(notification);

        if (fcmService != null) {
            userRepository.findById(userId).ifPresent(user -> {
                if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                    Map<String, String> data = new HashMap<>();
                    data.put("type", type.name());
                    data.put("notificationId", String.valueOf(saved.getId()));
                    fcmService.sendNotification(user.getFcmToken(), "WeConnect", message, data);
                }
            });
        }
    }

    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo."));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAsActioned(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo."));
        notification.setActioned(true);
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    public int getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }
}
