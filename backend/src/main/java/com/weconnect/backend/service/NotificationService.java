package com.weconnect.backend.service;

import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.repository.BlockedUserRepository;
import com.weconnect.backend.repository.NotificationRepository;
import com.weconnect.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BlockedUserRepository blockedUserRepository;

    @Autowired(required = false)
    private FCMService fcmService;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               BlockedUserRepository blockedUserRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.blockedUserRepository = blockedUserRepository;
    }

    public List<Notification> getNotifications(Long userId) {
        List<Notification> all = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return all.stream()
                .filter(n -> !isFriendRequestHidden(n, userId))
                .collect(Collectors.toList());
    }

    /**
     * Ẩn notification lời mời kết bạn nếu đang có block relationship với sender.
     * Notification đã actioned (accepted/declined) vẫn hiển thị nhưng không có nút hành động.
     */
    private boolean isFriendRequestHidden(Notification n, Long currentUserId) {
        if (n.getType() != Notification.NotificationType.FRIEND_REQUEST_RECEIVED) return false;
        if (n.getRelatedUserId() != null) {
            Long senderId = n.getRelatedUserId();
            boolean blockedByMe = blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, senderId);
            boolean blockedByThem = blockedUserRepository.existsByBlockerIdAndBlockedId(senderId, currentUserId);
            if (blockedByMe || blockedByThem) return true;
        }
        return false;
    }

    @Transactional
    public void createNotification(Long userId, Notification.NotificationType type,
                                   String message, String relatedUsername) {
        createNotification(userId, type, message, relatedUsername, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotificationWithoutPush(Long userId, Notification.NotificationType type,
                                              String message, String relatedUsername) {
        createNotificationInternal(userId, type, message, relatedUsername, null, null, null, false);
    }

    @Transactional
    public void createNotification(Long userId, Notification.NotificationType type,
                                   String message, String relatedUsername,
                                   Long relatedPostId, Long relatedUserId) {
        createNotificationInternal(userId, type, message, relatedUsername, relatedPostId, relatedUserId, null, true);
    }

    @Transactional
    public void createNotificationForReport(Long userId, Notification.NotificationType type,
                                            String message, String relatedUsername, Long relatedReportId) {
        createNotificationInternal(userId, type, message, relatedUsername, null, null, relatedReportId, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotificationForReportWithoutPush(Long userId, Notification.NotificationType type,
                                                       String message, String relatedUsername, Long relatedReportId) {
        createNotificationInternal(userId, type, message, relatedUsername, null, null, relatedReportId, false);
    }

    private void createNotificationInternal(Long userId, Notification.NotificationType type,
                                            String message, String relatedUsername,
                                            Long relatedPostId, Long relatedUserId, Long relatedReportId,
                                            boolean sendPush) {
        log.info("[NOTIFY-INTERNAL] Lưu notification: userId={}, type={}, relatedReportId={}", userId, type, relatedReportId);
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .relatedUsername(relatedUsername)
                .relatedPostId(relatedPostId)
                .relatedUserId(relatedUserId)
                .relatedReportId(relatedReportId)
                .isRead(false)
                .isActioned(false)
                .build();
        Notification saved = notificationRepository.save(notification);
        log.info("[NOTIFY-INTERNAL] Đã lưu notification id={} cho userId={}, type={}", saved.getId(), userId, type);

        // Real-time delivery via STOMP (khi app đang online)
        if (messagingTemplate != null) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("notificationId", saved.getId());
                payload.put("type", type.name());
                payload.put("message", message);
                if (relatedReportId != null) payload.put("relatedReportId", relatedReportId);
                if (relatedPostId != null) payload.put("relatedPostId", relatedPostId);
                if (relatedUserId != null) payload.put("relatedUserId", relatedUserId);
                messagingTemplate.convertAndSendToUser(
                        userId.toString(), "/queue/notifications", payload);
            } catch (Exception ignored) {}
        }

        // FCM push notification (khi app offline). Một số luồng cần tự gửi FCM với title/body riêng,
        // nên sendPush=false sẽ chỉ lưu DB + realtime WebSocket để tránh bắn trùng push.
        if (sendPush && fcmService != null) {
            try {
                userRepository.findById(userId).ifPresent(user -> {
                    if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                        Map<String, String> data = new HashMap<>();
                        data.put("type", type.name());
                        data.put("notificationId", String.valueOf(saved.getId()));
                        if (relatedReportId != null) data.put("relatedReportId", String.valueOf(relatedReportId));
                        if (relatedPostId != null) data.put("relatedPostId", String.valueOf(relatedPostId));
                        fcmService.sendNotification(user.getFcmToken(), "WeConnect", message, data);
                    }
                });
            } catch (Exception e) {
                log.warn("FCM delivery failed for user {}: {}", userId, e.getMessage());
            }
        }
    }

    @Transactional
    public void createChatSummaryNotification(Long userId, String message, Long relatedRoomId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(Notification.NotificationType.CHAT_SUMMARY)
                .message(message)
                .relatedRoomId(relatedRoomId)
                .isRead(false)
                .isActioned(false)
                .build();
        Notification saved = notificationRepository.save(notification);

        if (messagingTemplate != null) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("notificationId", saved.getId());
                payload.put("type", Notification.NotificationType.CHAT_SUMMARY.name());
                payload.put("message", message);
                payload.put("relatedRoomId", relatedRoomId);
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", payload);
            } catch (Exception ignored) {}
        }

        if (fcmService != null) {
            try {
                userRepository.findById(userId).ifPresent(user -> {
                    if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                        Map<String, String> data = new HashMap<>();
                        data.put("type", Notification.NotificationType.CHAT_SUMMARY.name());
                        data.put("notificationId", String.valueOf(saved.getId()));
                        data.put("relatedRoomId", String.valueOf(relatedRoomId));
                        fcmService.sendNotification(user.getFcmToken(), "WeConnect", message, data);
                    }
                });
            } catch (Exception e) {
                log.warn("FCM delivery failed for user {}: {}", userId, e.getMessage());
            }
        }
    }

    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo."));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAsActioned(Long notificationId) {
        markAsActioned(notificationId, null);
    }

    public void markAsActioned(Long notificationId, String actionResult) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo."));
        notification.setActioned(true);
        notification.setRead(true);
        if (actionResult != null && !actionResult.isEmpty()) {
            notification.setActionResult(actionResult);
        }
        notificationRepository.save(notification);
    }

    // Đánh dấu FRIEND_REQUEST_RECEIVED notification là actioned khi user accept/decline
    @Transactional
    public void markFriendRequestActioned(Long receiverId, Long senderId, String actionResult) {
        List<Notification> notifs = notificationRepository.findByUserIdAndTypeAndRelatedUserId(
                receiverId, Notification.NotificationType.FRIEND_REQUEST_RECEIVED, senderId);
        for (Notification n : notifs) {
            if (!n.isActioned()) {
                n.setActioned(true);
                n.setRead(true);
                n.setActionResult(actionResult);
            }
        }
        if (!notifs.isEmpty()) notificationRepository.saveAll(notifs);
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

    // Đánh dấu tất cả JOIN_REQUEST notification của user cho một post là đã đọc
    @Transactional
    public void markJoinRequestsAsRead(Long userId, Long postId) {
        List<Notification> notifs = notificationRepository.findByUserIdAndTypeAndRelatedPostId(
                userId, Notification.NotificationType.JOIN_REQUEST, postId);
        boolean changed = false;
        for (Notification n : notifs) {
            if (!n.isRead()) {
                n.setRead(true);
                changed = true;
            }
        }
        if (changed) notificationRepository.saveAll(notifs);
    }

    // Khi chủ bài duyệt/từ chối member: đánh dấu notification JOIN_REQUEST tương ứng là actioned
    @Transactional
    public void markJoinRequestActioned(Long ownerId, Long memberId, Long postId) {
        List<Notification> notifs = notificationRepository.findByUserIdAndTypeAndRelatedUserIdAndRelatedPostId(
                ownerId, Notification.NotificationType.JOIN_REQUEST, memberId, postId);
        for (Notification n : notifs) {
            n.setActioned(true);
            n.setRead(true);
        }
        if (!notifs.isEmpty()) notificationRepository.saveAll(notifs);
    }

    // Khi A chặn B: hủy các notification lời mời kết bạn từ B gửi cho A
    @Transactional
    public void cancelFriendRequestNotifications(Long receiverId, Long senderId) {
        List<Notification> notifs = notificationRepository.findByUserIdAndTypeAndRelatedUserId(
                receiverId, Notification.NotificationType.FRIEND_REQUEST_RECEIVED, senderId);
        for (Notification notif : notifs) {
            if (!notif.isActioned()) {
                notif.setActioned(true);
                notif.setRead(true);
            }
        }
        if (!notifs.isEmpty()) {
            notificationRepository.saveAll(notifs);
        }
    }
}
