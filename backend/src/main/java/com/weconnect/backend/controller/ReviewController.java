package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
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
