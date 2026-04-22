package com.weconnect.backend.service;

import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.Report;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.ReportRepository;
import com.weconnect.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;
    private final NotificationService notificationService;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         PostRepository postRepository,
                         PostMemberRepository postMemberRepository,
                         NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
        this.notificationService = notificationService;
    }

    // User tạo report từ app
    public String createReport(Long reporterId, String targetType, Long targetId,
                                String reason, String description) {
        Report.TargetType type;
        try {
            type = Report.TargetType.valueOf(targetType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("targetType phải là USER hoặc POST.");
        }

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(type)
                .targetId(targetId)
                .reason(reason)
                .description(description)
                .status(Report.Status.PENDING)
                .build();

        reportRepository.save(report);
        return "Gửi báo cáo thành công!";
    }

    // === Admin Notification (Report-based) ===

    /** Đếm report chưa xem bởi admin */
    public int getUnviewedReportCount() {
        return reportRepository.countByAdminViewedFalse();
    }

    /** Lấy tất cả reports (cho notification dropdown) */
    public List<Map<String, Object>> getReportNotifications() {
        List<Report> reports = reportRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Report r : reports) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("reporterId", r.getReporterId());

            User reporter = userRepository.findById(r.getReporterId()).orElse(null);
            map.put("reporterName", reporter != null ? reporter.getFullName() : "Unknown");

            map.put("targetType", r.getTargetType().name());
            map.put("targetId", r.getTargetId());
            map.put("reason", r.getReason());
            map.put("status", r.getStatus().name());
            map.put("adminViewed", r.isAdminViewed());
            map.put("createdAt", r.getCreatedAt());

            // Tên target
            if (r.getTargetType() == Report.TargetType.POST) {
                Post post = postRepository.findById(r.getTargetId()).orElse(null);
                if (post != null) {
                    String content = post.getContent();
                    map.put("targetName", content != null && content.length() > 50
                            ? content.substring(0, 50) + "..." : content);
                }
            } else {
                User targetUser = userRepository.findById(r.getTargetId()).orElse(null);
                map.put("targetName", targetUser != null ? targetUser.getFullName() : "Unknown");
            }

            result.add(map);
        }
        return result;
    }

    /** Đánh dấu 1 report đã xem */
    @Transactional
    public void markReportViewed(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));
        report.setAdminViewed(true);
        reportRepository.save(report);
    }

    /** Đánh dấu tất cả reports đã xem */
    @Transactional
    public void markAllReportsViewed() {
        List<Report> unviewed = reportRepository.findByAdminViewedFalseOrderByCreatedAtDesc();
        for (Report r : unviewed) {
            r.setAdminViewed(true);
        }
        reportRepository.saveAll(unviewed);
    }

    // Lấy tất cả reports (cho admin)
    public List<Map<String, Object>> getAllReports() {
        List<Report> reports = reportRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Report r : reports) {
            result.add(buildReportMap(r));
        }
        return result;
    }

    // Lấy chi tiết report (cho admin)
    public Map<String, Object> getReportById(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));
        return buildReportMap(report);
    }

    // Admin cập nhật status report
    public String updateReportStatus(Long id, String status, Long adminId) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));

        Report.Status newStatus;
        try {
            newStatus = Report.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Status phải là PENDING, REVIEWED hoặc RESOLVED.");
        }

        report.setStatus(newStatus);
        report.setReviewedAt(LocalDateTime.now());
        report.setReviewedBy(adminId);
        reportRepository.save(report);
        return "Cập nhật trạng thái thành công!";
    }

    /**
     * Admin xử lý report với hành động cụ thể.
     * Actions: WARN, HIDE_POST, DELETE_POST, BLOCK_USER, DELETE_USER, NO_VIOLATION
     * Trả về actionLabel để controller gửi notification sau khi transaction commit.
     */
    @Transactional
    public String[] resolveReport(Long reportId, String action, Long adminId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));

        if (report.getStatus() == Report.Status.RESOLVED) {
            throw new RuntimeException("Report này đã được xử lý rồi.");
        }

        String actionUpper = action.toUpperCase();
        Long targetId = report.getTargetId();
        String actionLabel;

        switch (actionUpper) {
            case "WARN" -> {
                actionLabel = handleWarn(report);
            }
            case "HIDE_POST" -> {
                actionLabel = handleHidePost(targetId);
            }
            case "DELETE_POST" -> {
                actionLabel = handleDeletePost(targetId);
            }
            case "BLOCK_USER" -> {
                actionLabel = handleBlockUser(targetId);
            }
            case "DELETE_USER" -> {
                actionLabel = handleDeleteUser(targetId);
            }
            case "NO_VIOLATION" -> {
                actionLabel = "Đánh dấu không vi phạm";
            }
            default -> throw new RuntimeException("Action không hợp lệ: " + action);
        }

        // Cập nhật report
        report.setStatus(Report.Status.RESOLVED);
        report.setAdminAction(actionUpper);
        report.setReviewedAt(LocalDateTime.now());
        report.setReviewedBy(adminId);
        reportRepository.save(report);

        // Trả về info để controller gửi notification sau khi transaction xong
        return new String[]{
                actionUpper,
                actionLabel,
                String.valueOf(report.getReporterId()),
                report.getTargetType().name(),
                String.valueOf(report.getTargetId())
        };
    }

    /**
     * Gửi notifications sau khi resolve thành công (gọi từ controller, ngoài transaction)
     */
    public void sendResolveNotifications(String action, String actionLabel,
                                          Long reporterId, String targetType, Long targetId) {
        // Gửi notification cho người bị report (target)
        try {
            Long targetUserId = null;
            String msg = null;

            if ("USER".equals(targetType)) {
                if ("NO_VIOLATION".equals(action) || "DELETE_USER".equals(action)) {
                    // Không gửi nếu không vi phạm hoặc user đã bị xóa
                } else {
                    targetUserId = targetId;
                    msg = "Admin đã xử lý báo cáo liên quan đến bạn: " + actionLabel + ".";
                }
            } else {
                if (!"NO_VIOLATION".equals(action)) {
                    Post post = postRepository.findById(targetId).orElse(null);
                    if (post != null) {
                        targetUserId = post.getAuthorId();
                        msg = "Admin đã xử lý báo cáo bài viết của bạn: " + actionLabel + ".";
                    }
                }
            }

            if (targetUserId != null && msg != null) {
                Notification.NotificationType nType = "WARN".equals(action)
                        ? Notification.NotificationType.ADMIN_WARNING
                        : Notification.NotificationType.ADMIN_ACTION;
                notificationService.createNotification(targetUserId, nType, msg, "Admin", null, null);
            }
        } catch (Exception ignored) {}

        // Gửi notification cho người đã gửi report
        try {
            String msg = "Báo cáo của bạn đã được admin xử lý: " + actionLabel + ". Cảm ơn bạn đã gửi báo cáo!";
            notificationService.createNotification(reporterId,
                    Notification.NotificationType.ADMIN_ACTION, msg, "Admin", null, null);
        } catch (Exception ignored) {}
    }

    // --- Action handlers ---

    private String handleWarn(Report report) {
        if (report.getTargetType() == Report.TargetType.USER) {
            userRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new RuntimeException("User không tồn tại."));
            return "Cảnh cáo người dùng";
        } else {
            Post post = postRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại."));
            // Cảnh cáo chủ bài viết
            userRepository.findById(post.getAuthorId())
                    .orElseThrow(() -> new RuntimeException("Tác giả bài viết không tồn tại."));
            return "Cảnh cáo người đăng bài";
        }
    }

    private String handleHidePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại."));
        post.setArchived(true);
        postRepository.save(post);
        return "Ẩn bài viết";
    }

    private String handleDeletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại."));
        postMemberRepository.deleteByPostId(postId);
        postRepository.delete(post);
        return "Xóa bài viết";
    }

    private String handleBlockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại."));
        user.setBlocked(true);
        userRepository.save(user);
        return "Khóa tài khoản";
    }

    private String handleDeleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại."));
        userRepository.delete(user);
        return "Xóa tài khoản";
    }

    // --- Notification helpers ---

    private void sendTargetNotification(Report report, String action, String actionLabel) {
        Long targetUserId;
        String msg;

        if (report.getTargetType() == Report.TargetType.USER) {
            targetUserId = report.getTargetId();
            if ("NO_VIOLATION".equals(action)) return; // không thông báo nếu không vi phạm
            if ("DELETE_USER".equals(action)) return; // user đã bị xóa, không gửi được
            msg = "Admin đã xử lý báo cáo liên quan đến bạn: " + actionLabel + ".";
        } else {
            // POST target — notify author
            Post post = null;
            try {
                post = postRepository.findById(report.getTargetId()).orElse(null);
            } catch (Exception ignored) {}

            if (post == null) return; // post đã bị xóa
            targetUserId = post.getAuthorId();

            if ("NO_VIOLATION".equals(action)) return;
            msg = "Admin đã xử lý báo cáo bài viết của bạn: " + actionLabel + ".";
        }

        try {
            Notification.NotificationType nType = "WARN".equals(action)
                    ? Notification.NotificationType.ADMIN_WARNING
                    : Notification.NotificationType.ADMIN_ACTION;

            notificationService.createNotification(targetUserId, nType,
                    msg, "Admin", null, null);
        } catch (Exception ignored) {}
    }

    private void sendReporterNotification(Report report, String actionLabel) {
        try {
            String msg = "Báo cáo của bạn đã được admin xử lý: " + actionLabel + ". Cảm ơn bạn đã gửi báo cáo!";
            notificationService.createNotification(report.getReporterId(),
                    Notification.NotificationType.ADMIN_ACTION,
                    msg, "Admin", null, null);
        } catch (Exception ignored) {}
    }

    private Map<String, Object> buildReportMap(Report r) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", r.getId());
        map.put("reporterId", r.getReporterId());

        User reporter = userRepository.findById(r.getReporterId()).orElse(null);
        map.put("reporterName", reporter != null ? reporter.getFullName() : "Unknown");

        map.put("targetType", r.getTargetType().name());
        map.put("targetId", r.getTargetId());
        map.put("reason", r.getReason());
        map.put("description", r.getDescription());
        map.put("status", r.getStatus().name());
        map.put("adminAction", r.getAdminAction());
        map.put("createdAt", r.getCreatedAt());
        map.put("reviewedAt", r.getReviewedAt());
        map.put("reviewedBy", r.getReviewedBy());

        // Thêm thông tin chi tiết của target
        if (r.getTargetType() == Report.TargetType.POST) {
            Post post = postRepository.findById(r.getTargetId()).orElse(null);
            if (post != null) {
                Map<String, Object> targetInfo = new HashMap<>();
                targetInfo.put("content", post.getContent());
                targetInfo.put("interestTag", post.getInterestTag());
                targetInfo.put("location", post.getLocation());
                targetInfo.put("imageUrl", post.getImageUrl());
                targetInfo.put("authorId", post.getAuthorId());
                targetInfo.put("maxMembers", post.getMaxMembers());
                targetInfo.put("startTime", post.getStartTime());
                targetInfo.put("endTime", post.getEndTime());
                targetInfo.put("archived", post.isArchived());
                targetInfo.put("createdAt", post.getCreatedAt());
                // Lấy tên tác giả
                User author = userRepository.findById(post.getAuthorId()).orElse(null);
                targetInfo.put("authorName", author != null ? author.getFullName() : "Unknown");
                map.put("targetInfo", targetInfo);
            }
        } else if (r.getTargetType() == Report.TargetType.USER) {
            User targetUser = userRepository.findById(r.getTargetId()).orElse(null);
            if (targetUser != null) {
                Map<String, Object> targetInfo = new HashMap<>();
                targetInfo.put("fullName", targetUser.getFullName());
                targetInfo.put("email", targetUser.getEmail());
                targetInfo.put("bio", targetUser.getBio());
                targetInfo.put("interestTags", targetUser.getInterestTags());
                targetInfo.put("avatarUrl", targetUser.getAvatarUrl());
                targetInfo.put("averageRating", targetUser.getAverageRating());
                targetInfo.put("reputationScore", targetUser.getReputationScore());
                targetInfo.put("isBlocked", targetUser.isBlocked());
                targetInfo.put("gender", targetUser.getGender());
                targetInfo.put("createdAt", targetUser.getCreatedAt());
                map.put("targetInfo", targetInfo);
            }
        }

        return map;
    }
}

