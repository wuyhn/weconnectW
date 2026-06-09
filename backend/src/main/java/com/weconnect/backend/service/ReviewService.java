package com.weconnect.backend.service;

import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.PostMember;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.entity.UserReview;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.ReportRepository;
import com.weconnect.backend.repository.SystemViolationLogRepository;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.repository.UserReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ReviewService {

    private final UserReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PostMemberRepository postMemberRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final SystemViolationLogRepository systemViolationLogRepository;
    private final ReputationSanctionService reputationSanctionService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ReviewService(UserReviewRepository reviewRepository, UserRepository userRepository,
                         PostMemberRepository postMemberRepository, PostRepository postRepository,
                         ReportRepository reportRepository,
                         SystemViolationLogRepository systemViolationLogRepository,
                         ReputationSanctionService reputationSanctionService) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.postMemberRepository = postMemberRepository;
        this.postRepository = postRepository;
        this.reportRepository = reportRepository;
        this.systemViolationLogRepository = systemViolationLogRepository;
        this.reputationSanctionService = reputationSanctionService;
    }

    // Lấy danh sách review của 1 user
    public List<Map<String, Object>> getReviews(Long userId) {
        List<UserReview> reviews = reviewRepository.findByReviewedUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserReview r : reviews) {
            result.add(buildReviewMap(r));
        }
        return result;
    }

    // Tạo đánh giá mới
    public Map<String, Object> createReview(Long reviewerId, Long reviewedUserId,
                                            Long postId, Integer rating,
                                            String reputationLabel, String comment) {
        if (reviewerId.equals(reviewedUserId)) {
            throw new RuntimeException("Không thể đánh giá chính mình.");
        }

        // Kiểm tra đã đánh giá chưa
        if (reviewRepository.existsByReviewerIdAndReviewedUserId(reviewerId, reviewedUserId)) {
            throw new RuntimeException("Bạn đã đánh giá người dùng này rồi. Hãy chỉnh sửa đánh giá cũ.");
        }

        // Validate postId
        if (postId == null) {
            throw new RuntimeException("Vui lòng chọn hoạt động chung.");
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Hoạt động không tồn tại."));

        // Kiểm tra hoạt động đã kết thúc chưa
        if (!isActivityEnded(post)) {
            throw new RuntimeException("Bạn có thể đánh giá sau khi hoạt động kết thúc.");
        }

        // Kiểm tra cả 2 đều tham gia hoạt động này
        if (!isParticipant(reviewerId, post)) {
            throw new RuntimeException("Bạn chưa tham gia hoạt động này.");
        }
        if (!isParticipant(reviewedUserId, post)) {
            throw new RuntimeException("Người được đánh giá chưa tham gia hoạt động này.");
        }

        // Derive activityName từ post
        String activityName = buildActivityName(post);

        UserReview review = UserReview.builder()
                .reviewerId(reviewerId)
                .reviewedUserId(reviewedUserId)
                .postId(postId)
                .rating(rating)
                .activityName(activityName)
                .reputationLabel(reputationLabel)
                .comment(comment)
                .build();

        UserReview saved = reviewRepository.save(review);
        recalcAverageRating(reviewedUserId);
        recalculateReputation(reviewedUserId);
        return buildReviewMap(saved);
    }

    // Chỉnh sửa đánh giá
    public Map<String, Object> updateReview(Long reviewId, Long currentUserId,
                                            Integer rating, String reputationLabel, String comment) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Đánh giá không tồn tại."));

        if (!review.getReviewerId().equals(currentUserId)) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa đánh giá này.");
        }

        Integer oldRating = review.getRating();
        if (rating != null) review.setRating(rating);
        if (reputationLabel != null && !reputationLabel.isEmpty()) review.setReputationLabel(reputationLabel);
        if (comment != null) review.setComment(comment);
        review.setUpdatedAt(LocalDateTime.now());

        UserReview saved = reviewRepository.save(review);
        recalcAverageRating(review.getReviewedUserId());
        recalculateReputation(review.getReviewedUserId());
        return buildReviewMap(saved);
    }

    // Xóa đánh giá
    public void deleteReview(Long reviewId, Long currentUserId) {
        UserReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Đánh giá không tồn tại."));

        if (!review.getReviewerId().equals(currentUserId)) {
            throw new RuntimeException("Bạn không có quyền xóa đánh giá này.");
        }

        Long reviewedUserId = review.getReviewedUserId();

        reviewRepository.delete(review);
        recalcAverageRating(reviewedUserId);
        recalculateReputation(reviewedUserId);
    }

    // Kiểm tra quyền đánh giá
    public Map<String, Object> canReview(Long reviewerId, Long reviewedUserId) {
        Map<String, Object> result = new HashMap<>();

        if (reviewerId.equals(reviewedUserId)) {
            result.put("canReview", false);
            result.put("reason", "Không thể đánh giá chính mình.");
            return result;
        }

        // Đã đánh giá rồi — trả về review cũ
        Optional<UserReview> existing = reviewRepository.findByReviewerIdAndReviewedUserId(reviewerId, reviewedUserId);
        if (existing.isPresent()) {
            result.put("canReview", false);
            result.put("reason", "Bạn đã đánh giá người dùng này rồi.");
            result.put("existingReviewId", existing.get().getId());
            result.put("existingReview", buildReviewMap(existing.get()));
            return result;
        }

        // Kiểm tra hoạt động chung đã kết thúc
        List<Map<String, Object>> endedActivities = getCommonActivities(reviewerId, reviewedUserId);
        if (!endedActivities.isEmpty()) {
            result.put("canReview", true);
            result.put("reason", "");
            result.put("commonActivities", endedActivities);
            return result;
        }

        // Không có hoạt động chung đã kết thúc — kiểm tra có hoạt động chung nào không
        Set<Long> user1PostIds = getParticipatedPostIds(reviewerId);
        Set<Long> user2PostIds = getParticipatedPostIds(reviewedUserId);
        Set<Long> commonIds = new HashSet<>(user1PostIds);
        commonIds.retainAll(user2PostIds);

        if (commonIds.isEmpty()) {
            result.put("canReview", false);
            result.put("reason", "Bạn chỉ có thể đánh giá người đã từng tham gia hoạt động cùng bạn.");
        } else {
            result.put("canReview", false);
            result.put("reason", "Bạn có thể đánh giá sau khi hoạt động kết thúc.");
        }
        return result;
    }

    // Lấy đánh giá của reviewerId dành cho reviewedUserId
    public Map<String, Object> getMyReviewOf(Long reviewerId, Long reviewedUserId) {
        return reviewRepository.findByReviewerIdAndReviewedUserId(reviewerId, reviewedUserId)
                .map(this::buildReviewMap)
                .orElse(null);
    }

    // Lấy tất cả reviews (cho admin)
    public List<Map<String, Object>> getAllReviews() {
        List<UserReview> reviews = reviewRepository.findAll();
        reviews.sort((a, b) -> {
            if (b.getCreatedAt() == null || a.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserReview r : reviews) {
            result.add(buildReviewMap(r));
        }
        return result;
    }

    // Lấy 1 review theo ID (cho admin)
    public Map<String, Object> getReviewById(Long id) {
        UserReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review không tồn tại."));
        return buildReviewMap(review);
    }

    // Lấy danh sách hoạt động chung đã kết thúc giữa 2 user
    public List<Map<String, Object>> getCommonActivities(Long userId1, Long userId2) {
        Set<Long> user1PostIds = getParticipatedPostIds(userId1);
        Set<Long> user2PostIds = getParticipatedPostIds(userId2);

        Set<Long> commonIds = new HashSet<>(user1PostIds);
        commonIds.retainAll(user2PostIds);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long postId : commonIds) {
            Optional<Post> optPost = postRepository.findById(postId);
            if (!optPost.isPresent()) continue;
            Post post = optPost.get();

            // Chỉ trả về hoạt động đã kết thúc
            if (!isActivityEnded(post)) continue;

            Map<String, Object> map = new HashMap<>();
            map.put("postId", post.getId());
            String content = post.getContent() != null ? post.getContent() : "";
            if (content.length() > 80) content = content.substring(0, 80) + "...";
            map.put("activityName", content);
            map.put("interestTag", post.getInterestTag());

            LocalDateTime startTime = post.getStartTime();
            LocalDateTime endTime = post.getActivityEndTime() != null ? post.getActivityEndTime() : post.getEndTime();
            map.put("activityStartTime", startTime != null ? startTime.format(DATE_FMT) : null);
            map.put("activityEndTime", endTime != null ? endTime.format(DATE_FMT) : null);
            map.put("activityDateDisplay", buildActivityDateDisplay(post));
            result.add(map);
        }
        return result;
    }

    // ======================== PRIVATE HELPERS ========================

    private Map<String, Object> buildReviewMap(UserReview r) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", r.getId());
        map.put("reviewerId", r.getReviewerId());
        map.put("reviewedUserId", r.getReviewedUserId());
        map.put("postId", r.getPostId());
        map.put("rating", r.getRating());

        User reviewer = userRepository.findById(r.getReviewerId()).orElse(null);
        map.put("reviewerName", reviewer != null ? reviewer.getFullName() : "Ẩn danh");
        map.put("reviewerAvatarUrl", reviewer != null ? reviewer.getAvatarUrl() : null);

        User reviewed = userRepository.findById(r.getReviewedUserId()).orElse(null);
        map.put("reviewedUserName", reviewed != null ? reviewed.getFullName() : "Unknown");
        map.put("reviewedUserAvatarUrl", reviewed != null ? reviewed.getAvatarUrl() : null);

        map.put("activityName", r.getActivityName());
        map.put("reputationLabel", r.getReputationLabel());
        map.put("comment", r.getComment());
        map.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FMT) : null);
        map.put("updatedAt", r.getUpdatedAt() != null ? r.getUpdatedAt().format(DATE_FMT) : null);
        map.put("isEdited", r.getUpdatedAt() != null);

        // Thông tin hoạt động chung
        if (r.getPostId() != null) {
            postRepository.findById(r.getPostId()).ifPresent(post -> {
                map.put("interestTag", post.getInterestTag());
                LocalDateTime endTime = post.getActivityEndTime() != null ? post.getActivityEndTime() : post.getEndTime();
                map.put("activityStartTime", post.getStartTime() != null ? post.getStartTime().format(DATE_FMT) : null);
                map.put("activityEndTime", endTime != null ? endTime.format(DATE_FMT) : null);
                map.put("activityDateDisplay", buildActivityDateDisplay(post));
            });
        }

        return map;
    }

    private boolean isActivityEnded(Post post) {
        LocalDateTime now = LocalDateTime.now();
        if (post.getActivityEndTime() != null) {
            return now.isAfter(post.getActivityEndTime());
        }
        return post.getEndTime() != null && now.isAfter(post.getEndTime());
    }

    private boolean isParticipant(Long userId, Post post) {
        if (userId.equals(post.getAuthorId())) return true;
        List<PostMember> memberships = postMemberRepository.findByUserIdAndStatus(userId, PostMember.Status.APPROVED);
        for (PostMember pm : memberships) {
            if (pm.getPostId().equals(post.getId())) return true;
        }
        return false;
    }

    private Set<Long> getParticipatedPostIds(Long userId) {
        Set<Long> postIds = new HashSet<>();
        List<Post> authoredPosts = postRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
        for (Post p : authoredPosts) {
            postIds.add(p.getId());
        }
        List<PostMember> memberships = postMemberRepository.findByUserIdAndStatus(userId, PostMember.Status.APPROVED);
        for (PostMember pm : memberships) {
            postIds.add(pm.getPostId());
        }
        return postIds;
    }

    private String buildActivityName(Post post) {
        String tag = post.getInterestTag() != null ? "[" + post.getInterestTag() + "] " : "";
        String content = post.getContent() != null ? post.getContent() : "";
        if (content.length() > 60) content = content.substring(0, 60) + "...";
        return tag + content;
    }

    private String buildActivityDateDisplay(Post post) {
        String tag = post.getInterestTag() != null ? post.getInterestTag() : "";
        LocalDateTime startTime = post.getStartTime();
        LocalDateTime endTime = post.getActivityEndTime() != null ? post.getActivityEndTime() : post.getEndTime();

        if (startTime == null) return tag;

        String startStr = startTime.format(DATE_FMT);
        if (endTime != null && !endTime.toLocalDate().equals(startTime.toLocalDate())) {
            return "Đã cùng tham gia: " + tag + " - " + startStr + " - " + endTime.format(DATE_FMT);
        }
        return "Đã cùng tham gia: " + tag + " - " + startStr;
    }

    // ======================== REPUTATION HELPERS ========================

    /**
     * Tính lại reputationScore hoàn toàn từ dữ liệu lịch sử trong DB — không phụ thuộc
     * vào bất kỳ trường tích lũy nào trên bảng User.
     *
     * Công thức:
     *   ratingScore          = 60.0 (mặc định) hoặc (avgRating / 5) * 100 nếu đã có review
     *   reportPenalty        = SUM(penaltyPoint) từ bảng reports với status=VALID
     *   violationPenaltySum  = SUM(penaltyPoint) từ bảng system_violation_logs (âm = tha phạt)
     *   reputationScore      = clamp(ratingScore - reportPenalty - max(0, violationPenaltySum), 0, 100)
     *
     * Sau khi lưu điểm mới, tự động gọi ReputationSanctionService để áp chế tài nếu cần.
     */
    @Transactional
    public void recalculateReputation(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {

            // --- Thành phần 1: ratingScore từ các đánh giá ---
            double ratingScore;
            int reviewCount = reviewRepository.countByReviewedUserId(userId);
            if (reviewCount == 0) {
                ratingScore = 60.0;
            } else {
                List<UserReview> reviews = reviewRepository.findByReviewedUserIdOrderByCreatedAtDesc(userId);
                double averageRating = reviews.stream()
                        .filter(r -> r.getRating() != null && r.getRating() > 0)
                        .mapToInt(UserReview::getRating)
                        .average()
                        .orElse(0.0);
                user.setAverageRating((float) averageRating);
                ratingScore = (averageRating / 5.0) * 100.0;
            }

            // --- Thành phần 2: reportPenalty — SUM từ bảng reports (VALID) ---
            int userPenalty = reportRepository.sumValidUserReportPenalties(userId);
            List<Long> postIds = postRepository.findByAuthorId(userId)
                    .stream().map(Post::getId).toList();
            int postPenalty = postIds.isEmpty() ? 0
                    : reportRepository.sumValidPostReportPenaltiesByPostIds(postIds);
            int reportPenalty = userPenalty + postPenalty;

            // --- Thành phần 3: violationPenalty — SUM từ system_violation_logs ---
            // Giá trị âm trong bảng là record tha phạt (LOCK_EXPIRY_FORGIVENESS).
            // clamp về 0 để tha phạt không biến thành "cộng thêm điểm".
            int rawViolationSum = systemViolationLogRepository.sumPenaltyByUserId(userId);
            int violationPenalty = Math.max(0, rawViolationSum);

            // Đồng bộ cache trên User để admin UI vẫn hiển thị được giá trị ròng
            user.setViolationPenaltySum(violationPenalty);

            // --- Tính toán và lưu ---
            double computed = ratingScore - reportPenalty - violationPenalty;
            double clampedScore = Math.max(0.0, Math.min(100.0, computed));
            user.setReputationScore(clampedScore);
            userRepository.save(user);

            // --- Áp chế tài nếu điểm về 0 (có idempotency guard bên trong) ---
            reputationSanctionService.applySanctionBasedOnScore(userId, clampedScore);
        });
    }

    public double calculateReputationBaseScore(Long userId) {
        List<UserReview> reviews = reviewRepository.findByReviewedUserIdOrderByCreatedAtDesc(userId);

        double ratingScore;
        if (reviews.isEmpty()) {
            ratingScore = 60.0;
        } else {
            double avg = reviews.stream()
                    .filter(r -> r.getRating() != null && r.getRating() > 0)
                    .mapToInt(UserReview::getRating)
                    .average()
                    .orElse(3.0);
            ratingScore = (avg / 5.0) * 100.0;
        }

        int userPenalty = reportRepository.sumValidUserReportPenalties(userId);
        List<Long> postIds = postRepository.findByAuthorId(userId)
                .stream().map(Post::getId).toList();
        int postPenalty = postIds.isEmpty() ? 0
                : reportRepository.sumValidPostReportPenaltiesByPostIds(postIds);
        int reportPenalty = userPenalty + postPenalty;

        return Math.max(0.0, Math.min(100.0, ratingScore - reportPenalty));
    }

    private void recalcAverageRating(Long userId) {
        List<UserReview> reviews = reviewRepository.findByReviewedUserIdOrderByCreatedAtDesc(userId);
        double avg = reviews.stream()
                .filter(r -> r.getRating() != null && r.getRating() > 0)
                .mapToInt(UserReview::getRating)
                .average()
                .orElse(0.0);
        userRepository.findById(userId).ifPresent(user -> {
            user.setAverageRating((float) avg);
            userRepository.save(user);
        });
    }
}
