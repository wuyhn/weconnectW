package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService,
                                  UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // Danh sách thông báo (enriched with senderAvatarUrl)
    @GetMapping
    public ResponseEntity<?> getNotifications(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<Notification> notifications = notificationService.getNotifications(user.getId());

        // Enrich each notification with sender's avatar URL
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Notification n : notifications) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", n.getId());
            item.put("userId", n.getUserId());
            item.put("type", n.getType());
            item.put("message", n.getMessage());
            item.put("relatedUsername", n.getRelatedUsername());
            item.put("relatedPostId", n.getRelatedPostId());
            item.put("relatedUserId", n.getRelatedUserId());
            item.put("read", n.isRead());
            item.put("actioned", n.isActioned());
            item.put("createdAt", n.getCreatedAt());
            // Lookup sender avatar from relatedUserId
            if (n.getRelatedUserId() != null) {
                userRepository.findById(n.getRelatedUserId()).ifPresent(sender ->
                    item.put("senderAvatarUrl", sender.getAvatarUrl())
                );
            }
            enriched.add(item);
        }

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(enriched).build());
    }

    // Đánh dấu đã đọc
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        try {
            notificationService.markAsRead(id);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã đánh dấu đã đọc.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1012).message(e.getMessage()).build());
        }
    }

    // Đọc tất cả
    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã đọc tất cả thông báo.").build());
    }

    // Đánh dấu đã xử lý
    @PutMapping("/{id}/action")
    public ResponseEntity<?> markAsActioned(@PathVariable Long id) {
        try {
            notificationService.markAsActioned(id);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã đánh dấu đã xử lý.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1012).message(e.getMessage()).build());
        }
    }

    // Số chưa đọc
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(notificationService.getUnreadCount(user.getId())).build());
    }
}
