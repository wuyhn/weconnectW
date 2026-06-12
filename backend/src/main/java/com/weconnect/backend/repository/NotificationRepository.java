package com.weconnect.backend.repository;

import com.weconnect.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    int countByUserIdAndIsReadFalse(Long userId);

    List<Notification> findByUserIdAndIsReadFalse(Long userId);

    // Tất cả notifications của user (dùng cho cascade delete)
    List<Notification> findByUserId(Long userId);

    List<Notification> findByUserIdAndTypeAndRelatedUserId(Long userId, Notification.NotificationType type, Long relatedUserId);

    List<Notification> findByUserIdAndTypeAndRelatedPostId(Long userId, Notification.NotificationType type, Long relatedPostId);

    List<Notification> findByUserIdAndTypeAndRelatedUserIdAndRelatedPostId(Long userId, Notification.NotificationType type, Long relatedUserId, Long relatedPostId);

    List<Notification> findByRelatedReportId(Long relatedReportId);

    boolean existsByUserIdAndRelatedReportIdAndType(Long userId, Long relatedReportId, Notification.NotificationType type);
}
