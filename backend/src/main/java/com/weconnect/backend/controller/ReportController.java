package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // === User endpoints (từ app) ===

    // User gửi report
    @PostMapping("/reports")
    public ResponseEntity<?> createReport(Authentication authentication,
                                           @RequestBody Map<String, Object> body) {
        User user = (User) authentication.getPrincipal();
        String targetType = (String) body.get("targetType");
        Long targetId = Long.valueOf(body.get("targetId").toString());
        String reason = (String) body.get("reason");
        String description = body.get("description") != null ? (String) body.get("description") : "";

        try {
            String result = reportService.createReport(user.getId(), targetType, targetId, reason, description);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1040).message(e.getMessage()).build());
        }
    }

    // === Admin endpoints (cho web admin) ===

    // Lấy tất cả reports
    @GetMapping("/admin/reports")
    public ResponseEntity<?> getAllReports() {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reportService.getAllReports()).build());
    }

    // === Admin Notification (Report-based) ===

    // Lấy danh sách report notifications
    @GetMapping("/admin/report-notifications")
    public ResponseEntity<?> getReportNotifications() {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reportService.getReportNotifications()).build());
    }

    // Đếm report chưa xem
    @GetMapping("/admin/report-notifications/unread-count")
    public ResponseEntity<?> getUnviewedReportCount() {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reportService.getUnviewedReportCount()).build());
    }

    // Đánh dấu 1 report đã xem
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

    // Đánh dấu tất cả reports đã xem
    @PutMapping("/admin/report-notifications/viewed-all")
    public ResponseEntity<?> markAllReportsViewed() {
        reportService.markAllReportsViewed();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã đánh dấu tất cả đã xem.").build());
    }

    // Lấy chi tiết 1 report
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

    // Admin cập nhật status report
    @PutMapping("/admin/reports/{id}/status")
    public ResponseEntity<?> updateReportStatus(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body,
                                                 Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        String status = (String) body.get("status");

        try {
            String result = reportService.updateReportStatus(id, status, admin.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1042).message(e.getMessage()).build());
        }
    }

    // Admin xử lý report với hành động cụ thể
    @PostMapping("/admin/reports/{id}/resolve")
    public ResponseEntity<?> resolveReport(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body,
                                             Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        String action = (String) body.get("action");

        try {
            // Bước 1: Xử lý DB (trong transaction)
            String[] result = reportService.resolveReport(id, action, admin.getId());

            // Bước 2: Gửi notification (ngoài transaction, không ảnh hưởng report)
            reportService.sendResolveNotifications(
                    result[0], result[1],
                    Long.parseLong(result[2]),
                    result[3],
                    Long.parseLong(result[4])
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã xử lý report: " + result[1]).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1043).message(e.getMessage()).build());
        }
    }
}
