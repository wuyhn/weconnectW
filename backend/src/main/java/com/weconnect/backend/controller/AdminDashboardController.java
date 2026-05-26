package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.repository.UserReviewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        stats.put("topInterestTags", getTopInterestTags(5));

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(stats).build());
    }

    // Thống kê hoạt động theo ngày (cho chart)
    private List<Map<String, Object>> getTopInterestTags(int limit) {
        Map<String, Long> tagCounts = new HashMap<>();

        userRepository.findAll().stream()
                .filter(user -> user.getRole() == 0)
                .map(user -> user.getInterestTags())
                .filter(tags -> tags != null && !tags.isBlank())
                .flatMap(tags -> Arrays.stream(tags.split(",")))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .forEach(tag -> tagCounts.merge(tag, 1L, Long::sum));

        return tagCounts.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tag", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .toList();
    }

    @GetMapping("/trends")
    public ResponseEntity<?> getTrends(@RequestParam(defaultValue = "7") int days) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("dd/MM");
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = now.toLocalDate().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = start.plusDays(1);

            long newUsers = userRepository.countByCreatedAtBetween(start, end);
            long newPosts = postRepository.countByCreatedAtBetween(start, end);
            long newReviews = userReviewRepository.countByCreatedAtBetween(start, end);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.format(labelFmt));
            point.put("users", newUsers);
            point.put("posts", newPosts);
            point.put("reviews", newReviews);
            result.add(point);
        }

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(result).build());
    }
}
