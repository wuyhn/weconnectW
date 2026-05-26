package com.weconnect.backend.service;

import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.Report;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.ReportRepository;
import com.weconnect.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;
    private final NotificationService notificationService;
    private final ReviewService reviewService;
    private final UserService userService;
    private final FCMService fcmService;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         PostRepository postRepository,
                         PostMemberRepository postMemberRepository,
                         NotificationService notificationService,
                         ReviewService reviewService,
                         UserService userService,
                         FCMService fcmService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
        this.notificationService = notificationService;
        this.reviewService = reviewService;
        this.userService = userService;
        this.fcmService = fcmService;
    }

    // User tạo report từ app
    public String createReport(Long reporterId, String targetType, Long targetId,
                                String reason, String description, List<String> imageUrls) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Vui lòng chọn lý do báo cáo.");
        }

        Report.TargetType type;
        try {
            type = Report.TargetType.valueOf(targetType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("targetType phải là USER hoặc POST.");
        }

        if (type == Report.TargetType.USER) {
            if (reporterId.equals(targetId)) {
                throw new RuntimeException("Không thể báo cáo chính mình.");
            }
            userRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Người dùng bị báo cáo không tồn tại."));
        } else {
            postRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Bài viết bị báo cáo không tồn tại."));
        }

        String imagesJson = urlsToJson(imageUrls);

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(type)
                .targetId(targetId)
                .reason(reason)
                .description(description)
                .evidenceImages(imagesJson)
                .status(Report.Status.PENDING)
                .build();

        reportRepository.save(report);
        return "Gửi báo cáo thành công!";
    }

    // === Admin: Xác nhận báo cáo hợp lệ (VALID) ===

    @Transactional
    public Map<String, Object> approveReport(Long reportId, Long adminId, Integer penaltyPoint) {
        log.info("[REPORT-APPROVE] START reportId={}, adminId={}, penaltyInput={}", reportId, adminId, penaltyPoint);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));

        if (report.getStatus() == Report.Status.VALID) {
            throw new RuntimeException("Report này đã được xác nhận rồi.");
        }

        int penalty = penaltyPoint != null ? penaltyPoint
                : suggestPenaltyPoint(report.getReason(), report.getTargetType());
        penalty = Math.max(0, penalty);

        User reporter = userRepository.findById(report.getReporterId())
                .orElseThrow(() -> new RuntimeException("Nguoi bao cao khong ton tai."));

        Long targetUserId = resolveTargetUserId(report);
        if (targetUserId == null) {
            throw new RuntimeException("Khong xac dinh duoc nguoi bi bao cao.");
        }

        User reportedUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Nguoi bi bao cao khong ton tai."));

        report.setStatus(Report.Status.VALID);
        report.setPenaltyPoint(penalty);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        reportRepository.save(report);

        boolean sanctionApplied = false;
        log.info("[REPORT-APPROVE] Report {} da set VALID, reporterId={}, targetUserId={}, penalty={}",
                reportId, reporter.getId(), reportedUser.getId(), penalty);

        // 1) Thong bao cho nguoi bao cao: bao cao da duoc Ban quan tri xac minh va xu ly.
        String reporterTitle = "🎉 Kết quả xử lý báo cáo từ WeConnect";
        String reporterBody = "Cảm ơn bạn đã gửi phản hồi. Báo cáo vi phạm của bạn đối với người dùng "
                + displayName(reportedUser)
                + " đã được Ban quản trị xác minh và xử lý vi phạm thành công. "
                + "Cảm ơn bạn đã chung tay xây dựng cộng đồng văn minh!";
        try {
            notificationService.createNotificationForReportWithoutPush(
                    reporter.getId(), Notification.NotificationType.REPORT_CONFIRMED, reporterBody, "Admin", reportId);
            log.info("[REPORT-APPROVE] Da luu REPORT_CONFIRMED notification cho reporterId={}, reportId={}",
                    reporter.getId(), reportId);
        } catch (Exception e) {
            // Notification chi la kenh thong bao phu. Khong de loi schema/FCM lam rollback approve report va tru diem.
            log.error("[REPORT-APPROVE] Luu REPORT_CONFIRMED notification that bai, tiep tuc xu ly reportId={}, reporterId={}, error={}",
                    reportId, reporter.getId(), e.getMessage(), e);
        }
        sendFcmToUserSafely(reporter, reporterTitle, reporterBody, "REPORTER_APPROVED", reportId);

        if (penalty > 0) {
            // 2) Thong bao va che tai nguoi bi bao cao qua UserService de dung mot luong tru diem/khoa tai khoan.
            // penaltyPoint da duoc luu o report VALID, nen overload nay khong cong vao violationPenaltySum.
            String reportedTitle = "🚫 Cảnh báo xác nhận vi phạm từ Admin";
            String reportedBody = "Hành vi của bạn đã bị cộng đồng báo cáo vi phạm tiêu chuẩn cộng đồng "
                    + "và đã được Admin xác nhận. Bạn vừa bị trừ " + penalty + " điểm uy tín.";
            userService.handleApprovedReportViolation(
                    reportedUser.getId(), penalty, reportedTitle, reportedBody, "REPORT_APPROVE:" + reportId);
            sanctionApplied = true;
            log.info("[REPORT-APPROVE] Da goi handleApprovedReportViolation targetUserId={}, penalty={}",
                    reportedUser.getId(), penalty);
        } else {
            log.info("[REPORT-APPROVE] Report {} penalty=0, bo qua luong tru diem user.", reportId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "VALID");
        result.put("penaltyPoint", penalty);
        result.put("reportId", reportId);
        result.put("targetType", report.getTargetType().name());
        result.put("targetUserId", targetUserId);
        result.put("reporterId", report.getReporterId());
        result.put("sanctionApplied", sanctionApplied);
        log.info("[REPORT-APPROVE] END reportId={}, reporterId={}, targetUserId={}, sanctionApplied={}",
                reportId, reporter.getId(), targetUserId, sanctionApplied);
        return result;
    }

    // === Admin: Từ chối báo cáo (REJECTED) ===

    @Transactional
    public Map<String, Object> rejectReport(Long reportId, Long adminId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));

        if (report.getStatus() == Report.Status.REJECTED) {
            throw new RuntimeException("Report này đã bị từ chối rồi.");
        }

        boolean wasValid = report.getStatus() == Report.Status.VALID;

        report.setStatus(Report.Status.REJECTED);
        report.setPenaltyPoint(0);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        reportRepository.save(report);

        // Chỉ cần recalculate nếu trước đó là VALID (reverting penalty)
        if (wasValid) {
            Long targetUserId = resolveTargetUserId(report);
            if (targetUserId != null) {
                reviewService.recalculateReputation(targetUserId);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "REJECTED");
        result.put("reportId", reportId);
        result.put("reporterId", report.getReporterId());
        return result;
    }

    // === Admin: Ẩn bài viết từ report (POST target only) ===

    @Transactional
    public String hidePostForReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));
        if (report.getTargetType() != Report.TargetType.POST) {
            throw new RuntimeException("Báo cáo này không nhắm vào bài viết.");
        }
        Post post = postRepository.findById(report.getTargetId())
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại."));
        post.setArchived(true);
        postRepository.save(post);
        return "Đã ẩn bài viết";
    }

    // === Admin: Xóa bài viết từ report (POST target only) ===

    @Transactional
    public String deletePostForReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));
        if (report.getTargetType() != Report.TargetType.POST) {
            throw new RuntimeException("Báo cáo này không nhắm vào bài viết.");
        }
        Long postId = report.getTargetId();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại."));
        postMemberRepository.deleteByPostId(postId);
        postRepository.delete(post);
        return "Đã xóa bài viết";
    }

    // === Gợi ý mức phạt dựa theo lý do báo cáo ===

    public int suggestPenaltyPoint(String reason, Report.TargetType targetType) {
        if (reason == null) return 10;
        String r = reason.trim().toLowerCase();
        if (targetType == Report.TargetType.USER) {
            return switch (r) {
                case "spam/làm phiền" -> 10;
                case "nội dung không phù hợp" -> 15;
                case "lừa đảo/giả mạo" -> 30;
                case "quấy rối/xúc phạm" -> 30;
                default -> 10;
            };
        } else {
            return switch (r) {
                case "spam/quảng cáo" -> 5;
                case "thông tin sai lệch" -> 10;
                case "nội dung thô tục" -> 10;
                case "vi phạm quy định" -> 10;
                case "quấy rối/bắt nạt" -> 20;
                default -> 10;
            };
        }
    }

    // === Lấy các mức phạt hợp lệ cho lý do báo cáo ===

    public List<Integer> getPenaltyOptions(String reason, Report.TargetType targetType) {
        if (reason == null) return List.of(5, 10, 15, 20, 30);
        String r = reason.trim().toLowerCase();
        if (targetType == Report.TargetType.USER) {
            return switch (r) {
                case "spam/làm phiền" -> List.of(10);
                case "nội dung không phù hợp" -> List.of(15);
                case "lừa đảo/giả mạo" -> List.of(30);
                case "quấy rối/xúc phạm" -> List.of(30);
                default -> List.of(5, 15, 30);
            };
        } else {
            return switch (r) {
                case "spam/quảng cáo" -> List.of(5);
                case "thông tin sai lệch" -> List.of(10);
                case "nội dung thô tục" -> List.of(10);
                case "vi phạm quy định" -> List.of(10, 20);
                case "quấy rối/bắt nạt" -> List.of(20);
                default -> List.of(5, 10, 20);
            };
        }
    }

    // === Admin Notification (Report-based) ===

    public int getUnviewedReportCount() {
        return reportRepository.countByAdminViewedFalse();
    }

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

    @Transactional
    public void markReportViewed(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));
        report.setAdminViewed(true);
        reportRepository.save(report);
    }

    @Transactional
    public void markAllReportsViewed() {
        List<Report> unviewed = reportRepository.findByAdminViewedFalseOrderByCreatedAtDesc();
        for (Report r : unviewed) {
            r.setAdminViewed(true);
        }
        reportRepository.saveAll(unviewed);
    }

    public List<Map<String, Object>> getAllReports() {
        List<Report> reports = reportRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Report r : reports) {
            result.add(buildReportMap(r));
        }
        return result;
    }

    public Map<String, Object> getReportById(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));
        return buildReportMap(report);
    }

    // === Notification helpers ===

    public void sendApproveNotifications(Long reporterId, String targetType, Long targetUserId,
                                         int penaltyPoint, Long reportId) {
        log.info("[NOTIFY] sendApproveNotifications START — reportId={}, targetType={}, targetUserId={}, reporterId={}, penalty={}",
                reportId, targetType, targetUserId, reporterId, penaltyPoint);

        // Notification cho User 2 (người bị báo cáo / chủ bài viết): REPORT_PENALTY
        if (targetUserId != null) {
            try {
                String msg = "USER".equals(targetType)
                        ? "Báo cáo về bạn đã được xác nhận. Điểm uy tín của bạn bị trừ " + penaltyPoint + " điểm."
                        : "Bài viết của bạn đã bị xác nhận vi phạm. Điểm uy tín của bạn bị trừ " + penaltyPoint + " điểm.";
                log.info("[NOTIFY] Tạo REPORT_PENALTY cho targetUserId={}, reportId={}, msg={}", targetUserId, reportId, msg);
                notificationService.createNotificationForReport(targetUserId,
                        Notification.NotificationType.REPORT_PENALTY, msg, "Admin", reportId);
                log.info("[NOTIFY] Tạo REPORT_PENALTY thành công cho targetUserId={}", targetUserId);
            } catch (Exception e) {
                log.error("[NOTIFY] Tạo REPORT_PENALTY THẤT BẠI cho user {}: {}", targetUserId, e.getMessage(), e);
            }
        } else {
            log.warn("[NOTIFY] targetUserId null — bỏ qua REPORT_PENALTY notification (reportId={})", reportId);
        }

        // Notification cho User 1 (người gửi báo cáo): REPORT_CONFIRMED, phân biệt theo targetType
        try {
            String reporterMsg = "USER".equals(targetType)
                    ? "Báo cáo của bạn về người dùng đã được admin xử lý."
                    : "Báo cáo của bạn về bài viết đã được admin xử lý.";
            log.info("[NOTIFY] Tạo REPORT_CONFIRMED cho reporterId={}, reportId={}", reporterId, reportId);
            notificationService.createNotificationForReport(reporterId,
                    Notification.NotificationType.REPORT_CONFIRMED, reporterMsg, "Admin", reportId);
            log.info("[NOTIFY] Tạo REPORT_CONFIRMED thành công cho reporterId={}", reporterId);
        } catch (Exception e) {
            log.error("[NOTIFY] Tạo REPORT_CONFIRMED THẤT BẠI cho reporter {}: {}", reporterId, e.getMessage(), e);
        }

        log.info("[NOTIFY] sendApproveNotifications END — reportId={}", reportId);
    }

    public Map<String, Object> getMyReportDetail(Long reportId, Long userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy báo cáo."));

        Long targetUserId = resolveTargetUserId(report);
        // targetUserId null chỉ xảy ra khi POST target mà bài viết đã bị xóa
        if (targetUserId == null) {
            throw new RuntimeException("Bài viết liên quan đến báo cáo này đã bị xóa.");
        }
        if (!userId.equals(targetUserId)) {
            throw new RuntimeException("Bạn không có quyền xem báo cáo này.");
        }

        Map<String, Object> map = new HashMap<>();
        map.put("id", report.getId());
        map.put("targetType", report.getTargetType().name());
        map.put("reason", report.getReason());
        map.put("status", report.getStatus().name());
        map.put("penaltyPoint", report.getPenaltyPoint());
        map.put("adminAction", report.getAdminAction());
        map.put("reviewedAt", report.getReviewedAt());

        User user = userRepository.findById(userId).orElse(null);
        map.put("currentReputationScore", user != null ? user.getReputationScore() : null);

        if (report.getTargetType() == Report.TargetType.POST) {
            Post post = postRepository.findById(report.getTargetId()).orElse(null);
            if (post != null) map.put("postContent", post.getContent());
        }

        return map;
    }

    public void sendRejectNotifications(Long reporterId, Long reportId) {
        try {
            notificationService.createNotificationForReport(reporterId,
                    Notification.NotificationType.ADMIN_ACTION,
                    "Báo cáo của bạn đã được xem xét nhưng không đủ cơ sở xác nhận vi phạm.",
                    "Admin", reportId);
        } catch (Exception e) {
            log.warn("Gửi ADMIN_ACTION notification cho reporter {} thất bại: {}", reporterId, e.getMessage());
        }
    }

    // === Helpers ===

    private void sendFcmToUserSafely(User user, String title, String body, String context, Long reportId) {
        if (user == null) {
            log.warn("[REPORT-FCM] Bo qua FCM vi user null, context={}, reportId={}", context, reportId);
            return;
        }

        String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.warn("[REPORT-FCM] User {} khong co fcmToken, bo qua push. context={}, reportId={}",
                    user.getId(), context, reportId);
            return;
        }

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", context);
            data.put("reportId", String.valueOf(reportId));
            log.info("[REPORT-FCM] Chuan bi gui FCM userId={}, context={}, reportId={}, title={}",
                    user.getId(), context, reportId, title);
            fcmService.sendNotification(token, title, body, data);
            log.info("[REPORT-FCM] Da goi fcmService.sendNotification userId={}, context={}, reportId={}",
                    user.getId(), context, reportId);
        } catch (Exception e) {
            log.error("[REPORT-FCM] Gui FCM that bai userId={}, context={}, reportId={}, error={}",
                    user.getId(), context, reportId, e.getMessage(), e);
        }
    }

    private String displayName(User user) {
        if (user == null) return "Unknown";
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
        return "ID " + user.getId();
    }

    private Long resolveTargetUserId(Report report) {
        if (report.getTargetType() == Report.TargetType.USER) {
            return report.getTargetId();
        }
        return postRepository.findById(report.getTargetId())
                .map(Post::getAuthorId)
                .orElse(null);
    }

    private Map<String, Object> buildReportMap(Report r) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", r.getId());
        map.put("reporterId", r.getReporterId());

        User reporter = userRepository.findById(r.getReporterId()).orElse(null);
        map.put("reporterName", reporter != null ? reporter.getFullName() : "Unknown");
        map.put("reporterAvatarUrl", reporter != null ? reporter.getAvatarUrl() : null);

        map.put("targetType", r.getTargetType().name());
        map.put("targetId", r.getTargetId());
        map.put("reason", r.getReason());
        map.put("description", r.getDescription());
        map.put("status", r.getStatus().name());
        map.put("penaltyPoint", r.getPenaltyPoint());
        map.put("suggestedPenalty", suggestPenaltyPoint(r.getReason(), r.getTargetType()));
        map.put("penaltyOptions", getPenaltyOptions(r.getReason(), r.getTargetType()));
        map.put("adminAction", r.getAdminAction());
        map.put("createdAt", r.getCreatedAt());
        map.put("reviewedAt", r.getReviewedAt());
        map.put("reviewedBy", r.getReviewedBy());

        map.put("evidenceImages", jsonToUrls(r.getEvidenceImages()));

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
                User author = userRepository.findById(post.getAuthorId()).orElse(null);
                targetInfo.put("authorName", author != null ? author.getFullName() : "Unknown");
                String content = post.getContent() != null ? post.getContent() : "";
                map.put("targetName", content.length() > 80 ? content.substring(0, 80) + "..." : content);
                map.put("targetThumbnailUrl", post.getImageUrl());
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
                targetInfo.put("violationPenaltySum", targetUser.getViolationPenaltySum());
                targetInfo.put("violationCount", targetUser.getViolationCount());
                targetInfo.put("status", targetUser.getStatus());
                targetInfo.put("lockUntil", targetUser.getLockUntil());
                targetInfo.put("isBlocked", targetUser.isBlocked());
                targetInfo.put("gender", targetUser.getGender());
                targetInfo.put("createdAt", targetUser.getCreatedAt());
                map.put("targetName", targetUser.getFullName());
                map.put("targetAvatarUrl", targetUser.getAvatarUrl());
                map.put("targetInfo", targetInfo);
            }
        }

        return map;
    }

    // --- JSON helpers ---

    private static String urlsToJson(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(urls.get(i)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static List<String> jsonToUrls(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return result;
        trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        if (trimmed.isEmpty()) return result;
        int i = 0;
        while (i < trimmed.length()) {
            int start = trimmed.indexOf('"', i);
            if (start < 0) break;
            int end = start + 1;
            while (end < trimmed.length()) {
                if (trimmed.charAt(end) == '\\') { end += 2; continue; }
                if (trimmed.charAt(end) == '"') break;
                end++;
            }
            if (end < trimmed.length()) {
                result.add(trimmed.substring(start + 1, end)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\"));
            }
            i = end + 1;
        }
        return result;
    }
}
