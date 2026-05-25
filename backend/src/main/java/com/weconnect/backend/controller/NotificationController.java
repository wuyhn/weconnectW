package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.PostRepository;
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
    private final PostRepository postRepository;

    public NotificationController(NotificationService notificationService,
                                  UserRepository userRepository,
                                  PostRepository postRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
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
            item.put("relatedReportId", n.getRelatedReportId());
            item.put("read", n.isRead());
            item.put("actioned", n.isActioned());
            item.put("actionResult", n.getActionResult());
            item.put("createdAt", n.getCreatedAt());
            // Lookup sender avatar from relatedUserId
            if (n.getRelatedUserId() != null) {
                userRepository.findById(n.getRelatedUserId()).ifPresent(sender ->
                    item.put("senderAvatarUrl", sender.getAvatarUrl())
                );
            }
            // Enrich JOIN_REQUEST with postTitle (tag + dates) to support frontend grouping
            if (n.getType() == Notification.NotificationType.JOIN_REQUEST && n.getRelatedPostId() != null) {
                postRepository.findById(n.getRelatedPostId()).ifPresent(post -> {
                    String tag = post.getInterestTag() != null ? post.getInterestTag() : "";
                    String startDate = formatPostDate(post.getStartTime());
                    String endDate = formatPostDate(post.getEndTime());
                    String title;
                    if (!tag.isEmpty() && !startDate.isEmpty()) {
                        if (!endDate.isEmpty() && !endDate.equals(startDate)) {
                            title = tag + " - " + startDate + " - " + endDate;
                        } else {
                            title = tag + " - " + startDate;
                        }
                    } else if (!tag.isEmpty()) {
                        title = tag;
                    } else if (!startDate.isEmpty()) {
                        title = startDate;
                    } else {
                        title = "";
                    }
                    if (!title.isEmpty()) item.put("postTitle", title);
                });
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

    // Đánh dấu đã xử lý (actionResult: "ACCEPTED" | "DECLINED" | null)
    @PutMapping("/{id}/action")
    public ResponseEntity<?> markAsActioned(@PathVariable Long id,
            @RequestParam(required = false) String actionResult) {
        try {
            notificationService.markAsActioned(id, actionResult);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã đánh dấu đã xử lý.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1012).message(e.getMessage()).build());
        }
    }

    // Đánh dấu tất cả JOIN_REQUEST của một post là đã đọc (dùng khi user click vào notification group)
    @PutMapping("/join-requests/{postId}/read")
    public ResponseEntity<?> markJoinRequestsRead(@PathVariable Long postId,
                                                   Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        notificationService.markJoinRequestsAsRead(user.getId(), postId);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã đánh dấu đã đọc.").build());
    }

    private String formatPostDate(java.time.LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
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
