package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.service.ReviewService;
import com.weconnect.backend.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReportService reportService;

    // Thư mục lưu ảnh bằng chứng — đồng bộ với WebConfig.java
    private static final String UPLOAD_DIR = "uploads/";

    public ReviewController(ReviewService reviewService, ReportService reportService) {
        this.reviewService = reviewService;
        this.reportService = reportService;
    }

    // Lấy danh sách review của user
    @GetMapping("/reviews/user/{userId}")
    public ResponseEntity<?> getReviews(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reviewService.getReviews(userId)).build());
    }

    // Tạo đánh giá mới
    @PostMapping("/reviews")
    public ResponseEntity<?> createReview(Authentication authentication,
                                          @RequestBody Map<String, Object> body) {
        User user = (User) authentication.getPrincipal();
        Long reviewedUserId = Long.valueOf(body.get("reviewedUserId").toString());
        Long postId = body.get("postId") != null ? Long.valueOf(body.get("postId").toString()) : null;
        Integer rating = body.get("rating") != null ? ((Number) body.get("rating")).intValue() : null;
        String reputationLabel = (String) body.get("reputationLabel");
        String comment = (String) body.get("comment");

        try {
            Map<String, Object> result = reviewService.createReview(
                    user.getId(), reviewedUserId, postId, rating, reputationLabel, comment);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đánh giá thành công").result(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1030).message(e.getMessage()).build());
        }
    }

    // Chỉnh sửa đánh giá
    @PutMapping("/reviews/{id}")
    public ResponseEntity<?> updateReview(Authentication authentication,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        User user = (User) authentication.getPrincipal();
        Integer rating = body.get("rating") != null ? ((Number) body.get("rating")).intValue() : null;
        String reputationLabel = (String) body.get("reputationLabel");
        String comment = (String) body.get("comment");

        try {
            Map<String, Object> result = reviewService.updateReview(id, user.getId(), rating, reputationLabel, comment);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Cập nhật đánh giá thành công").result(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1030).message(e.getMessage()).build());
        }
    }

    // Xóa đánh giá
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<?> deleteReview(Authentication authentication,
                                          @PathVariable Long id) {
        User user = (User) authentication.getPrincipal();
        try {
            reviewService.deleteReview(id, user.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã xóa đánh giá").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1030).message(e.getMessage()).build());
        }
    }

    // Kiểm tra quyền đánh giá (trả về canReview, reason, existingReview, commonActivities)
    @GetMapping("/reviews/can-review/{userId}")
    public ResponseEntity<?> canReview(Authentication authentication,
                                       @PathVariable Long userId) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reviewService.canReview(user.getId(), userId))
                .build());
    }

    // Lấy đánh giá của currentUser dành cho userId
    @GetMapping("/reviews/my-review/{userId}")
    public ResponseEntity<?> getMyReview(Authentication authentication,
                                         @PathVariable Long userId) {
        User user = (User) authentication.getPrincipal();
        Map<String, Object> review = reviewService.getMyReviewOf(user.getId(), userId);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(review)
                .build());
    }

    // Lấy danh sách hoạt động chung đã kết thúc giữa currentUser và userId
    @GetMapping("/reviews/common-activities/{userId}")
    public ResponseEntity<?> getCommonActivities(Authentication authentication,
                                                  @PathVariable Long userId) {
        User currentUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reviewService.getCommonActivities(currentUser.getId(), userId))
                .build());
    }

    // =========================================================================
    // Người dùng báo cáo nhận xét vu khống về mình (multipart/form-data)
    // =========================================================================
    // Nhận:
    //   reason        : văn bản giải trình chi tiết (bắt buộc)
    //   evidenceImage : ảnh bằng chứng (tùy chọn, tối đa 1 file)
    //
    // Quy trình:
    //   1. Lưu ảnh vào thư mục uploads/ → lấy URL tương đối /uploads/{tên file}
    //   2. Gọi reportService.createReport() với targetType=REVIEW, targetId=reviewId
    // =========================================================================
    @PostMapping("/reviews/{reviewId}/report")
    public ResponseEntity<?> reportReview(
            @PathVariable Long reviewId,
            @RequestParam("reason") String reason,
            @RequestParam(value = "evidenceImage", required = false) MultipartFile evidenceImage,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        // Lưu ảnh bằng chứng nếu có
        List<String> evidenceUrls = new ArrayList<>();
        if (evidenceImage != null && !evidenceImage.isEmpty()) {
            try {
                // Tạo tên file độc nhất tránh trùng lặp
                String originalName  = evidenceImage.getOriginalFilename();
                String ext           = (originalName != null && originalName.contains("."))
                        ? originalName.substring(originalName.lastIndexOf('.')) : ".jpg";
                String savedFileName = "report_" + UUID.randomUUID() + ext;

                // Tạo thư mục nếu chưa tồn tại
                Path uploadPath = Paths.get(UPLOAD_DIR);
                if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

                // Ghi file vào đĩa
                Path filePath = uploadPath.resolve(savedFileName);
                evidenceImage.transferTo(filePath.toFile());

                // URL tương đối để Glide/trình duyệt có thể truy cập qua WebConfig
                evidenceUrls.add("/uploads/" + savedFileName);
            } catch (IOException e) {
                return ResponseEntity.badRequest().body(ApiResponse.builder()
                        .code(1050)
                        .message("Không thể lưu ảnh bằng chứng: " + e.getMessage())
                        .build());
            }
        }

        try {
            String msg = reportService.createReport(
                    user.getId(),
                    "REVIEW",        // targetType cố định
                    reviewId,
                    reason,
                    reason,          // description = reason (giải trình chi tiết)
                    evidenceUrls);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(msg).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1040).message(e.getMessage()).build());
        }
    }

    // === Admin endpoints ===

    @GetMapping("/admin/reviews")
    public ResponseEntity<?> getAllReviews() {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(reviewService.getAllReviews()).build());
    }

    @GetMapping("/admin/reviews/{id}")
    public ResponseEntity<?> getReviewById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Thành công")
                    .result(reviewService.getReviewById(id)).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1031).message(e.getMessage()).build());
        }
    }
}
