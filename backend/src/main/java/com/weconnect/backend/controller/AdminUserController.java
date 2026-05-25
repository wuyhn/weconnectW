package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.PostMember;
import com.weconnect.backend.entity.Report;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.ReportRepository;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.repository.UserReviewRepository;
import com.weconnect.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;
    private final UserReviewRepository userReviewRepository;
    private final ReportRepository reportRepository;

    public AdminUserController(UserRepository userRepository, UserService userService,
                               PostRepository postRepository, PostMemberRepository postMemberRepository,
                               UserReviewRepository userReviewRepository, ReportRepository reportRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
        this.userReviewRepository = userReviewRepository;
        this.reportRepository = reportRepository;
    }

    // Lấy tất cả users (cho admin web)
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userRepository.findAll();
        // Convert to admin-friendly response with interestTags as array
        List<Map<String, Object>> result = users.stream().map(this::toAdminMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(result).build());
    }

    // Lấy chi tiết 1 user
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy user").build());
        }
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(toAdminMap(userOpt.get())).build());
    }

    // Block user
    @PutMapping("/{id}/block")
    public ResponseEntity<?> blockUser(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy user").build());
        }
        User user = userOpt.get();
        user.setBlocked(true);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã khóa tài khoản")
                .result(toAdminMap(user)).build());
    }

    // Unblock user
    @PutMapping("/{id}/unblock")
    public ResponseEntity<?> unblockUser(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy user").build());
        }
        User user = userOpt.get();
        user.setBlocked(false);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã mở khóa tài khoản")
                .result(toAdminMap(user)).build());
    }

    // Xóa user
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã xóa user").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1005).message(e.getMessage()).build());
        }
    }

    // Lấy thống kê hoạt động của user
    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getUserStats(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy user").build());
        }

        long totalPostsCreated = postRepository.findByAuthorId(id).size();
        long totalActivitiesJoined = postMemberRepository.findByUserIdAndStatus(id, PostMember.Status.APPROVED).size();
        long totalReviewsReceived = userReviewRepository.countByReviewedUserId(id);
        long totalReportsReceived = reportRepository.countByTargetTypeAndTargetId(Report.TargetType.USER, id);
        long confirmedViolations = reportRepository.countByTargetTypeAndTargetIdAndStatus(
                Report.TargetType.USER, id, Report.Status.VALID);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPostsCreated", totalPostsCreated);
        stats.put("totalActivitiesJoined", totalActivitiesJoined);
        stats.put("totalReviewsReceived", totalReviewsReceived);
        stats.put("totalReportsReceived", totalReportsReceived);
        stats.put("confirmedViolations", confirmedViolations);

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(stats).build());
    }

    // Helper: convert User entity to admin-friendly Map
    private Map<String, Object> toAdminMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("birthday", user.getBirthday());
        map.put("gender", user.getGender());
        map.put("avatarUrl", user.getAvatarUrl());
        map.put("bio", user.getBio());

        // Convert comma-separated string to array
        String tags = user.getInterestTags();
        if (tags != null && !tags.isEmpty()) {
            map.put("interestTags", Arrays.asList(tags.split(",")));
        } else {
            map.put("interestTags", List.of());
        }

        map.put("averageRating", user.getAverageRating());
        map.put("reputationScore", user.getReputationScore());
        map.put("isBlocked", user.isBlocked());
        map.put("role", user.getRole());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }
}
