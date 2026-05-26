package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.NotificationRepository;
import com.weconnect.backend.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;
    private final NotificationRepository notificationRepository;

    public ReportController(ReportService reportService, NotificationRepository notificationRepository) {
        this.reportService = reportService;
        this.notificationRepository = notificationRepository;
    }

    // === User endpoints ===

    @PostMapping("/reports")
    public ResponseEntity<?> createReport(Authentication authentication,
                                           @RequestBody Map<String, Object> body) {
        User user = (User) authentication.getPrincipal();
        String targetType = (String) body.get("targetType");
        Long targetId = Long.valueOf(body.get("targetId").toString());
        String reason = (String) body.get("reason");
        String description = body.get("description") != null ? (String) body.get("description") : "";

        List<String> evidenceImages = new java.util.ArrayList<>();
        Object imagesObj = body.get("evidenceImages");
        if (imagesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) evidenceImages.add(s);
            }
        }

        try {
            String result = reportService.createReport(user.getId(), targetType, targetId, reason, description, evidenceImages);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1040).message(e.getMessage()).build());
        }
    }

    @GetMapping("/reports/{id}/my-detail")
    public ResponseEntity<?> getMyReportDetail(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Thành công")
                    .result(reportService.getMyReportDetail(id, user.getId())).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1041).message(e.getMessage()).build());
        }
    }

    // === Admin endpoints ===

    @GetMapping("/admin/reports")
    public ResponseEntity<?> getAllReports() {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reportService.getAllReports()).build());
    }

    @GetMapping("/admin/reports/{id}")
    public ResponseEntity<?> getReportById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Thành công")
                    .result(reportService.getReportById(id)).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1041).message(e.getMessage()).build());
        }
    }

    // Admin xác nhận báo cáo hợp lệ (VALID) với điểm phạt
    @PostMapping("/admin/reports/{id}/approve")
    public ResponseEntity<?> approveReport(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        Integer penaltyPoint = null;
        if (body != null && body.get("penaltyPoint") != null) {
            penaltyPoint = Integer.valueOf(body.get("penaltyPoint").toString());
        }

        try {
            Map<String, Object> result = reportService.approveReport(id, admin.getId(), penaltyPoint);

            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message("Đã xác nhận báo cáo hợp lệ. Trừ " + result.get("penaltyPoint") + " điểm uy tín.")
                    .result(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1043).message(e.getMessage()).build());
        }
    }

    // Admin từ chối báo cáo (REJECTED)
    @PostMapping("/admin/reports/{id}/reject")
    public ResponseEntity<?> rejectReport(@PathVariable Long id,
                                           Authentication authentication) {
        User admin = (User) authentication.getPrincipal();

        try {
            Map<String, Object> result = reportService.rejectReport(id, admin.getId());

            reportService.sendRejectNotifications(
                    ((Number) result.get("reporterId")).longValue(),
                    ((Number) result.get("reportId")).longValue()
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã từ chối báo cáo.")
                    .result(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1043).message(e.getMessage()).build());
        }
    }

    // Admin ẩn bài viết được báo cáo
    @PostMapping("/admin/reports/{id}/hide-post")
    public ResponseEntity<?> hidePost(@PathVariable Long id) {
        try {
            String msg = reportService.hidePostForReport(id);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(msg).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1044).message(e.getMessage()).build());
        }
    }

    // Admin xóa bài viết được báo cáo
    @DeleteMapping("/admin/reports/{id}/delete-post")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        try {
            String msg = reportService.deletePostForReport(id);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(msg).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1044).message(e.getMessage()).build());
        }
    }

    // === Admin Notification (Report-based) ===

    @GetMapping("/admin/report-notifications")
    public ResponseEntity<?> getReportNotifications() {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reportService.getReportNotifications()).build());
    }

    @GetMapping("/admin/report-notifications/unread-count")
    public ResponseEntity<?> getUnviewedReportCount() {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reportService.getUnviewedReportCount()).build());
    }

    @PutMapping("/admin/report-notifications/{id}/viewed")
    public ResponseEntity<?> markReportViewed(@PathVariable Long id) {
        try {
            reportService.markReportViewed(id);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã đánh dấu đã xem.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1044).message(e.getMessage()).build());
        }
    }

    @PutMapping("/admin/report-notifications/viewed-all")
    public ResponseEntity<?> markAllReportsViewed() {
        reportService.markAllReportsViewed();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã đánh dấu tất cả đã xem.").build());
    }

    // === Diagnostic: kiểm tra notification đã lưu cho report chưa ===
    @GetMapping("/admin/reports/{id}/notification-status")
    public ResponseEntity<?> checkNotificationStatus(@PathVariable Long id) {
        List<Notification> notifs = notificationRepository.findByRelatedReportId(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : notifs) {
            Map<String, Object> m = new HashMap<>();
            m.put("notificationId", n.getId());
            m.put("userId", n.getUserId());
            m.put("type", n.getType());
            m.put("message", n.getMessage());
            m.put("isRead", n.isRead());
            m.put("createdAt", n.getCreatedAt());
            result.add(m);
        }
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000)
                .message("Tìm thấy " + result.size() + " notification(s) cho reportId=" + id)
                .result(result).build());
    }
}
