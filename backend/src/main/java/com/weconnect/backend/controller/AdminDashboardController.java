package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.repository.UserReviewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final UserReviewRepository userReviewRepository;

    public AdminDashboardController(UserRepository userRepository,
                                     PostRepository postRepository,
                                     UserReviewRepository userReviewRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.userReviewRepository = userReviewRepository;
    }

    // Thống kê cho dashboard
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long totalUsers = userRepository.count();
        long totalPosts = postRepository.count();
        long totalReviews = userReviewRepository.count();

        // Đếm users bị block
        long blockedUsers = userRepository.findAll().stream()
                .filter(u -> u.isBlocked()).count();

        // Đếm posts đã hết hạn (endTime < now) hoặc đã archived — source of truth là endTime
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        long archivedPosts = postRepository.findAll().stream()
                .filter(p -> p.isArchived() || (p.getEndTime() != null && p.getEndTime().isBefore(now)))
                .count();

        // Đếm posts còn hoạt động
        long activePosts = totalPosts - archivedPosts;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalPosts", totalPosts);
        stats.put("activePosts", activePosts);
        stats.put("totalReviews", totalReviews);
        stats.put("blockedUsers", blockedUsers);
        stats.put("archivedPosts", archivedPosts);

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(stats).build());
    }
}
