package com.weconnect.backend.service;

import com.weconnect.backend.dto.ChatMessageResponse;
import com.weconnect.backend.entity.ChatMessage;
import com.weconnect.backend.entity.ChatRoom;
import com.weconnect.backend.entity.ChatRoomMember;
import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.PostMember;
import com.weconnect.backend.entity.Report;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.enums.ViolationCode;
import com.weconnect.backend.entity.SystemViolationLog;
import com.weconnect.backend.entity.UserReview;
import com.weconnect.backend.repository.ChatMessageRepository;
import com.weconnect.backend.repository.ChatRoomMemberRepository;
import com.weconnect.backend.repository.ChatRoomRepository;
import com.weconnect.backend.repository.NotificationRepository;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.ReportRepository;
import com.weconnect.backend.repository.SystemViolationLogRepository;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.repository.UserReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
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

    // Self-injection qua @Lazy để gọi @Async method từ chính bean này mà vẫn qua Spring proxy.
    // Nếu gọi trực tiếp bằng this.handleHostSanctionEvent() sẽ bypass proxy → @Async không hoạt động.
    @Lazy
    @Autowired
    private ReportService self;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;
    private final NotificationService notificationService;
    private final ReviewService reviewService;
    private final FCMService fcmService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserReviewRepository userReviewRepository;
    private final SystemViolationLogRepository systemViolationLogRepository;
    private final NotificationRepository notificationRepository;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         PostRepository postRepository,
                         PostMemberRepository postMemberRepository,
                         NotificationService notificationService,
                         ReviewService reviewService,
                         FCMService fcmService,
                         ChatRoomRepository chatRoomRepository,
                         ChatRoomMemberRepository chatRoomMemberRepository,
                         ChatMessageRepository chatMessageRepository,
                         SimpMessagingTemplate messagingTemplate,
                         UserReviewRepository userReviewRepository,
                         SystemViolationLogRepository systemViolationLogRepository,
                         NotificationRepository notificationRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
        this.notificationService = notificationService;
        this.reviewService = reviewService;
        this.fcmService = fcmService;
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
        this.userReviewRepository = userReviewRepository;
        this.systemViolationLogRepository = systemViolationLogRepository;
        this.notificationRepository = notificationRepository;
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
            throw new RuntimeException("targetType phải là USER, POST hoặc REVIEW.");
        }

        if (type == Report.TargetType.USER) {
            if (reporterId.equals(targetId)) {
                throw new RuntimeException("Không thể báo cáo chính mình.");
            }
            userRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Người dùng bị báo cáo không tồn tại."));
        } else if (type == Report.TargetType.POST) {
            postRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Bài viết bị báo cáo không tồn tại."));
        } else {
            userReviewRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Đánh giá bị báo cáo không tồn tại."));
        }

        String imagesJson = urlsToJson(imageUrls);

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(type)
                .targetId(targetId)
                .reason(reason)
                .description(description)
                .detailReason(description)
                .evidenceImages(imagesJson)
                .status(Report.Status.PENDING)
                .build();

        reportRepository.save(report);
        return "Gửi báo cáo thành công!";
    }

    // =====================================================================
    // === MỚI: Phê duyệt báo cáo theo Mã Vi Phạm (ViolationCode Matrix) ===
    // =====================================================================
    //
    // Luồng xử lý đầy đủ (5 bước):
    //   Bước 1 → Tìm Report, đặt trạng thái VALID
    //   Bước 2 → Tính điểm phạt từ Ma trận Enum (hoặc customPenalty nếu là mã "Khác")
    //   Bước 3 → Xác định userId bị phạt; ẩn bài viết nếu targetType = POST
    //   Bước 4 → Gọi reviewService.recalculateReputation() để tái tính điểm uy tín
    //             theo công thức: clamp(ratingScore - reportPenalty - violationPenalty, 0, 100)
    //   Bước 5 → Thực thi chế tài và gửi thông báo (FCM + DB + WebSocket)
    //
    // @param reportId       ID của báo cáo cần xử lý
    // @param violationCode  Mã vi phạm (SPAM, FRAUD, SPAM_POST, U_OTHER, P_OTHER, ...)
    // @param customPenalty  Điểm phạt tùy chỉnh — chỉ dùng khi violationCode = U_OTHER / P_OTHER
    // @param adminNote      Ghi chú Admin — bắt buộc ≥ 10 ký tự khi dùng mã "Khác"
    //
    @Transactional
    public Map<String, Object> handleApprovedReportViolation(
            Long reportId,
            String violationCode,
            Integer customPenalty,
            String adminNote) {

        log.info("[VIOLATION-APPROVE] ▶ START — reportId={}, violationCode={}, customPenalty={}",
                reportId, violationCode, customPenalty);

        // ================================================================
        // BƯỚC 1: Tìm bản ghi Report và đánh dấu VALID
        // ================================================================
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException(
                        "Báo cáo #" + reportId + " không tồn tại trong hệ thống."));

        // Chặn phê duyệt lại báo cáo đã VALID để tránh trừ điểm trùng lặp
        if (report.getStatus() == Report.Status.VALID) {
            throw new RuntimeException("Báo cáo này đã được phê duyệt rồi. Không thể phê duyệt lại.");
        }

        // ================================================================
        // BƯỚC 2: Parse ViolationCode và tính điểm phạt
        // ================================================================
        ViolationCode code = parseViolationCode(violationCode);
        int penaltyPoint = determinePenaltyPoint(code, customPenalty, adminNote);

        // Cập nhật bản ghi Report: trạng thái VALID + điểm phạt đã xác định
        report.setStatus(Report.Status.VALID);
        report.setPenaltyPoint(penaltyPoint);
        report.setReviewedAt(LocalDateTime.now());

        // Nếu mã "Khác": ghi đè trường reason bằng nội dung giải trình của Admin
        // để thay thế chuỗi rác "U_OTHER"/"P_OTHER" mà user gửi lên ban đầu
        if (code.isCustomPenalty()) {
            report.setReason(adminNote.trim());
        }
        // Luôn lưu adminNote vào adminAction để hiển thị trong timeline lịch sử xử lý
        if (adminNote != null && !adminNote.isBlank()) {
            report.setAdminAction(adminNote.trim());
        }
        reportRepository.save(report);
        log.info("[VIOLATION-APPROVE] ✔ Đã lưu Report VALID — penaltyPoint={}, code={}",
                penaltyPoint, code.name());

        // ================================================================
        // BƯỚC 3: Xác định userId bị xử phạt theo loại báo cáo
        // ================================================================
        Long affectedUserId;
        Long hiddenPostId = null;
        Long maliciousReviewerId = null; // Chỉ dùng cho case REVIEW
        Report.TargetType targetType = report.getTargetType();
        boolean isReviewCase = (targetType == Report.TargetType.REVIEW);

        if (targetType == Report.TargetType.USER) {
            // Báo cáo USER → targetId chính là userId của người bị báo cáo
            affectedUserId = report.getTargetId();

        } else if (targetType == Report.TargetType.REVIEW) {
            // ============================================================
            // LUỒNG BÁO CÁO NHẬN XÉT VU KHỐNG
            // ============================================================
            // Khi Admin phê duyệt:
            //   1. Xóa nhận xét xấu khỏi bảng user_reviews
            //   2. Ghi SystemViolationLog phạt kẻ viết nhận xét vu khống
            //   3. Tái tính điểm kẻ vi phạm → áp chế tài nếu score ≤ 0
            //   4. affectedUserId = nạn nhân → Step 4 hoàn điểm tự động (review đã xóa)
            // ============================================================
            UserReview review = userReviewRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new RuntimeException(
                            "Nhận xét bị báo cáo không còn tồn tại hoặc đã bị gỡ trước đó."));

            maliciousReviewerId = review.getReviewerId();
            Long victimId        = review.getReviewedUserId();

            // Lưu ID kẻ vi phạm vào report để sau này kẻ vi phạm có thể xem chi tiết bị phạt
            report.setViolatorUserId(maliciousReviewerId);
            reportRepository.save(report);

            // Bước 3a: Xóa nhận xét vu khống khỏi DB
            userReviewRepository.deleteById(review.getId());
            log.info("[VIOLATION-APPROVE] ✔ Đã xóa nhận xét reviewId={} của userId={}",
                    review.getId(), maliciousReviewerId);

            // Bước 3b: Ghi phạt cho kẻ viết nhận xét vu khống vào SystemViolationLog
            // (violationPenalty = SUM từ bảng này được cộng vào công thức tính điểm)
            systemViolationLogRepository.save(SystemViolationLog.builder()
                    .userId(maliciousReviewerId)
                    .violationType("FAKE_REVIEW")
                    .penaltyPoint(penaltyPoint)
                    .build());
            log.info("[VIOLATION-APPROVE] ✔ Đã lưu SystemViolationLog phạt {} điểm cho maliciousReviewer userId={}",
                    penaltyPoint, maliciousReviewerId);

            // Bước 3c: Tái tính điểm uy tín cho kẻ vi phạm (tự động kích hoạt chế tài nếu cần)
            reviewService.recalculateReputation(maliciousReviewerId);

            // Bước 3d: Gửi thông báo phạt cho kẻ viết nhận xét vu khống
            User maliciousUser = userRepository.findById(maliciousReviewerId).orElse(null);
            if (maliciousUser != null) {
                double badActorScore = maliciousUser.getReputationScore();
                String penaltyMsg = "Nhận xét của bạn đã bị xác định là sai sự thật và bị gỡ bỏ bởi Admin. "
                        + "Tài khoản bị trừ " + penaltyPoint + " điểm uy tín. "
                        + "Điểm uy tín hiện tại: " + Math.round(badActorScore) + " điểm.";
                try {
                    notificationService.createNotificationForReport(
                            maliciousReviewerId,
                            Notification.NotificationType.REPORT_PENALTY,
                            penaltyMsg, "Admin", reportId);
                } catch (Exception ex) {
                    log.error("[VIOLATION-APPROVE] Gửi REPORT_PENALTY cho maliciousReviewer {} thất bại: {}",
                            maliciousReviewerId, ex.getMessage());
                }
                sendFcmToUserSafely(maliciousUser,
                        "🚫 Nhận xét bị gỡ bỏ do vi phạm",
                        penaltyMsg, "REVIEW_VIOLATION", reportId);
                // Kích hoạt chế tài dây chuyền nếu kẻ vi phạm vừa bị khóa
                if (badActorScore <= 0) {
                    log.info("[VIOLATION-APPROVE] maliciousReviewer userId={} bị khóa — kích hoạt handleHostSanctionEvent",
                            maliciousReviewerId);
                    self.handleHostSanctionEvent(maliciousReviewerId);
                }
            }

            // affectedUserId = nạn nhân → Step 4 sẽ hoàn điểm tự động vì review đã bị xóa
            affectedUserId = victimId;

        } else {
            // Báo cáo POST → phải tra bài viết để lấy authorId
            Post post = postRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new RuntimeException(
                            "Bài viết bị báo cáo không tồn tại hoặc đã bị xóa."));

            affectedUserId = post.getAuthorId();
            hiddenPostId = post.getId();

            // Ẩn bài viết vi phạm (archived = true tương đương trạng thái "Đã gỡ bỏ")
            // Không xóa cứng để admin có thể tra cứu lịch sử sau này
            post.setArchived(true);
            postRepository.save(post);
            log.info("[VIOLATION-APPROVE] ✔ Đã ẩn bài viết postId={}, authorId={}",
                    hiddenPostId, affectedUserId);
        }

        // ================================================================
        // BƯỚC 4: Tái tính toàn bộ điểm uy tín theo công thức đầy đủ
        // ================================================================
        // Với REVIEW case: affectedUserId = nạn nhân.
        //   Review đã bị xóa ở Bước 3 → recalculate sẽ không còn tính khoản âm từ review đó
        //   → điểm uy tín nạn nhân tự động được hoàn lại mà không cần tính thủ công.
        // Với USER/POST case: giống logic cũ.
        try {
            reviewService.recalculateReputation(affectedUserId);
            log.info("[VIOLATION-APPROVE] ✔ recalculateReputation hoàn tất cho userId={}", affectedUserId);
        } catch (Exception e) {
            log.error("[VIOLATION-APPROVE] ✘ recalculateReputation thất bại userId={}: {}",
                    affectedUserId, e.getMessage(), e);
            throw e;
        }

        // Đọc lại user từ DB để lấy điểm uy tín vừa được tính lại
        User affectedUser = userRepository.findById(affectedUserId)
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy người dùng bị ảnh hưởng (userId=" + affectedUserId + ")."));
        double newReputationScore = affectedUser.getReputationScore();

        log.info("[VIOLATION-APPROVE] userId={} | điểm uy tín mới = {}", affectedUserId, newReputationScore);

        // ================================================================
        // BƯỚC 5: Gửi thông báo — phân nhánh theo loại báo cáo
        // ================================================================
        if (isReviewCase) {
            // REVIEW CASE: Nạn nhân nhận thông báo tích cực (điểm được hoàn lại)
            // Không gửi cảnh báo "bị trừ điểm" vì nạn nhân là người bị hại, không phải vi phạm
            String restoreMsg = "Khiếu nại của bạn về nhận xét sai sự thật đã được Admin xét duyệt. "
                    + "Nhận xét đó đã bị gỡ bỏ và điểm uy tín của bạn được hoàn lại. "
                    + "Điểm uy tín hiện tại: " + Math.round(newReputationScore) + " điểm.";
            try {
                notificationService.createNotificationForReport(
                        affectedUserId,
                        Notification.NotificationType.REPORT_CONFIRMED,
                        restoreMsg, "Admin", reportId);
            } catch (Exception e) {
                log.error("[VIOLATION-APPROVE] Gửi REPORT_CONFIRMED cho victim {} thất bại: {}",
                        affectedUserId, e.getMessage());
            }
            sendFcmToUserSafely(affectedUser,
                    "✅ Nhận xét vu khống đã được gỡ bỏ",
                    restoreMsg, "REVIEW_RESTORED", reportId);

        } else {
            // USER / POST CASE: Logic thông báo phạt như cũ
            if (newReputationScore > 0) {
                // Score còn dương: cảnh báo, không khóa tài khoản
                String warnMsg = "Tài khoản của bạn bị trừ " + penaltyPoint
                        + " điểm uy tín do vi phạm [" + violationCode + "]. "
                        + "Điểm uy tín hiện tại: " + Math.round(newReputationScore) + " điểm. "
                        + "Hãy tuân thủ quy chuẩn cộng đồng để tránh bị khóa tài khoản.";
                try {
                    notificationService.createNotificationForReport(
                            affectedUserId,
                            Notification.NotificationType.ADMIN_WARNING,
                            warnMsg, "Admin", reportId);
                } catch (Exception e) {
                    log.error("[VIOLATION-APPROVE] Gửi ADMIN_WARNING thất bại userId={}: {}",
                            affectedUserId, e.getMessage());
                }
                sendFcmToUserSafely(affectedUser,
                        "⚠️ Cảnh báo vi phạm từ WeConnect",
                        "Tài khoản của bạn vừa bị trừ " + penaltyPoint
                                + " điểm uy tín. Điểm còn lại: " + Math.round(newReputationScore)
                                + " điểm. Vui lòng tuân thủ tiêu chuẩn cộng đồng.",
                        "VIOLATION_WARNING", reportId);
            }
            // Nếu score <= 0: ReputationSanctionService đã gửi notification lock/ban.
            if (newReputationScore <= 0) {
                log.info("[VIOLATION-APPROVE] Tài khoản userId={} vừa bị khóa — kích hoạt handleHostSanctionEvent",
                        affectedUserId);
                self.handleHostSanctionEvent(affectedUserId);
            }
            // Cảm ơn người đã gửi báo cáo (chỉ cho USER/POST — REVIEW tự xử lý ở trên)
            sendReporterConfirmNotification(report.getReporterId(), affectedUserId, reportId);
        }

        // Tổng hợp kết quả trả về cho Controller / Frontend Admin
        Map<String, Object> result = new HashMap<>();
        result.put("reportId", reportId);
        result.put("violationCode", violationCode);
        result.put("penaltyPoint", penaltyPoint);
        result.put("targetUserId", affectedUserId);
        result.put("hiddenPostId", hiddenPostId);
        result.put("newReputationScore", newReputationScore);
        result.put("userStatus", affectedUser.getStatus());
        result.put("userViolationCount", affectedUser.getViolationCount());
        // Dành riêng cho case REVIEW: ID của kẻ viết nhận xét vu khống đã bị phạt
        result.put("maliciousReviewerId", maliciousReviewerId);

        log.info("[VIOLATION-APPROVE] ■ DONE — reportId={}, userId={}, score={}, status={}",
                reportId, affectedUserId, newReputationScore, affectedUser.getStatus());
        return result;
    }

    // =====================================================================
    //  HELPER: Parse chuỗi violationCode → ViolationCode enum
    // =====================================================================

    /**
     * Chuyển đổi chuỗi violationCode (từ request) thành ViolationCode enum.
     * Ném IllegalArgumentException với danh sách mã hợp lệ nếu không nhận ra.
     */
    private ViolationCode parseViolationCode(String violationCodeStr) {
        if (violationCodeStr == null || violationCodeStr.isBlank()) {
            throw new IllegalArgumentException(
                    "Mã vi phạm (violationCode) không được để trống.");
        }
        try {
            return ViolationCode.valueOf(violationCodeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Mã vi phạm '" + violationCodeStr + "' không hợp lệ. "
                            + "Báo cáo USER: SPAM, INAPPROPRIATE, FRAUD, HARASSMENT, U_OTHER. "
                            + "Báo cáo POST: SPAM_POST, MISLEADING, VULGAR, VIOLATION, BULLYING, P_OTHER. "
                            + "Báo cáo REVIEW: FAKE_REVIEW, REVIEW_SPAM, R_OTHER.");
        }
    }

    // =====================================================================
    //  HELPER: Tính điểm phạt từ ViolationCode (hoặc customPenalty)
    // =====================================================================

    /**
     * Xác định điểm phạt cuối cùng:
     *   - Mã cố định → lấy thẳng từ Ma trận Enum (không cần thêm input)
     *   - Mã "Khác" (U_OTHER / P_OTHER) → validate customPenalty ∈ [0, 50]
     *                                     và adminNote có ít nhất 10 ký tự
     */
    private int determinePenaltyPoint(ViolationCode code, Integer customPenalty, String adminNote) {
        if (!code.isCustomPenalty()) {
            // Mã cố định — điểm phạt đã được định nghĩa sẵn trong Ma trận Enum
            return code.getFixedPenaltyPoint();
        }

        // ====== Xử lý mã "Khác" (U_OTHER / P_OTHER) ======

        // Kiểm tra adminNote: bắt buộc phải có ít nhất 10 ký tự giải trình
        // để đảm bảo Admin có lý do rõ ràng khi không dùng mã có sẵn
        if (adminNote == null || adminNote.trim().length() < 10) {
            throw new IllegalArgumentException(
                    "Khi chọn lý do 'Khác', Admin phải nhập ghi chú giải trình tối thiểu 10 ký tự.");
        }

        // Kiểm tra customPenalty: phải nằm trong khoảng [0, 50]
        // theo yêu cầu nghiệp vụ — giới hạn trên 50 điểm để tránh lạm dụng
        if (customPenalty == null || customPenalty < 0 || customPenalty > 50) {
            throw new IllegalArgumentException(
                    "Số điểm phạt tùy chỉnh cho lý do Khác phải nằm trong khoảng từ 0 đến 50 điểm!");
        }

        return customPenalty;
    }

    // =====================================================================
    //  HELPER: Gửi thông báo xác nhận cho người đã gửi báo cáo
    // =====================================================================

    /**
     * Gửi thông báo REPORT_CONFIRMED cho người đã báo cáo vi phạm:
     * cảm ơn họ đã tham gia xây dựng cộng đồng văn minh.
     * Dùng createNotificationForReportWithoutPush để tránh gửi FCM trùng lặp,
     * sau đó gửi FCM riêng với title/body rõ ràng.
     */
    private void sendReporterConfirmNotification(Long reporterId, Long reportedUserId, Long reportId) {
        try {
            User reportedUser = userRepository.findById(reportedUserId).orElse(null);
            String reportedName = displayName(reportedUser);

            String confirmMsg = "Cảm ơn bạn đã gửi báo cáo. Hành vi vi phạm của người dùng "
                    + reportedName
                    + " đã được Admin xác minh và xử lý thành công. "
                    + "Cảm ơn bạn đã chung tay xây dựng cộng đồng WeConnect văn minh!";

            // Lưu DB + WebSocket (không gửi FCM tránh duplicate)
            notificationService.createNotificationForReportWithoutPush(
                    reporterId,
                    Notification.NotificationType.REPORT_CONFIRMED,
                    confirmMsg,
                    "Admin",
                    reportId);

            // Gửi FCM push riêng với title thân thiện
            User reporter = userRepository.findById(reporterId).orElse(null);
            if (reporter != null) {
                sendFcmToUserSafely(
                        reporter,
                        "🎉 Kết quả báo cáo vi phạm từ WeConnect",
                        confirmMsg,
                        "REPORTER_CONFIRMED",
                        reportId);
            }
            log.info("[VIOLATION-APPROVE] ✔ Đã gửi REPORT_CONFIRMED cho reporterId={}", reporterId);
        } catch (Exception e) {
            // Notification cho reporter là kênh phụ — không rollback transaction chính
            log.error("[VIOLATION-APPROVE] ✘ Gửi REPORT_CONFIRMED thất bại reporterId={}: {}",
                    reporterId, e.getMessage());
        }
    }

    // === Admin: Xác nhận báo cáo hợp lệ (VALID) ===

    @Transactional
    public Map<String, Object> approveReport(Long reportId, Long adminId, Integer penaltyPoint, String adminNote) {
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
        if (adminNote != null && !adminNote.isBlank()) {
            report.setAdminAction(adminNote);
        }
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
            // 2) Tái tính điểm uy tín từ DB (penaltyPoint đã lưu vào report VALID ở trên).
            // recalculateReputation() query SUM từ bảng reports nên tự tính được khoản phạt mới.
            // ReputationSanctionService xử lý lock/ban nếu score về 0 (có idempotency guard).
            reviewService.recalculateReputation(reportedUser.getId());
            sanctionApplied = true;
            log.info("[REPORT-APPROVE] Da recalculate reputation targetUserId={}, penalty={}",
                    reportedUser.getId(), penalty);

            // Gửi thông báo cụ thể cho người bị phạt (nếu score > 0 — chưa bị khóa)
            User refreshed = userRepository.findById(reportedUser.getId()).orElse(reportedUser);
            double newScore = refreshed.getReputationScore();
            if (newScore > 0) {
                String reportedTitle = "🚫 Cảnh báo xác nhận vi phạm từ Admin";
                String reportedBody = "Hành vi của bạn đã bị cộng đồng báo cáo vi phạm tiêu chuẩn cộng đồng "
                        + "và đã được Admin xác nhận. Bạn vừa bị trừ " + penalty
                        + " điểm uy tín. Điểm còn lại: " + Math.round(newScore) + ".";
                try {
                    notificationService.createNotificationWithoutPush(
                            refreshed.getId(), Notification.NotificationType.ADMIN_WARNING,
                            reportedBody, "Admin");
                } catch (Exception e) {
                    log.error("[REPORT-APPROVE] Gửi ADMIN_WARNING thất bại userId={}: {}",
                            refreshed.getId(), e.getMessage());
                }
                sendFcmToUserSafely(refreshed, reportedTitle, reportedBody, "REPORT_APPROVED", reportId);
            }
            // Nếu score <= 0: ReputationSanctionService đã gửi notification lock/ban.
            // Kích hoạt xử lý dây chuyền bài đăng nếu tài khoản vừa bị khóa.
            if (newScore <= 0) {
                log.info("[REPORT-APPROVE] Tài khoản userId={} bị khóa — kích hoạt handleHostSanctionEvent", reportedUser.getId());
                self.handleHostSanctionEvent(reportedUser.getId());
            }
        } else {
            log.info("[REPORT-APPROVE] Report {} penalty=0, bỏ qua luồng trừ điểm user.", reportId);
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

    // === Admin: Phê duyệt báo cáo sai Tag — ẩn bài + trừ điểm + thông báo phạt tự động ===

    @Transactional
    public void approveWrongTagReport(Long reportId) {
        log.info("[WRONG-TAG-APPROVE] START reportId={}", reportId);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report không tồn tại."));

        if (report.getTargetType() != Report.TargetType.POST) {
            throw new RuntimeException("Chỉ áp dụng cho báo cáo bài viết (targetType = POST).");
        }
        if (report.getStatus() == Report.Status.VALID) {
            throw new RuntimeException("Report này đã được xử lý rồi.");
        }

        // Bước 1: Ẩn bài viết vi phạm
        Post post = postRepository.findById(report.getTargetId())
                .orElseThrow(() -> new RuntimeException("Bài viết bị báo cáo không còn tồn tại."));
        Long authorId = post.getAuthorId();
        post.setArchived(true);
        postRepository.save(post);
        log.info("[WRONG-TAG-APPROVE] Da an bai viet postId={}, authorId={}", post.getId(), authorId);

        // Bước 2: Đánh dấu Report đã xem và cập nhật trạng thái
        report.setAdminViewed(true);
        report.setStatus(Report.Status.VALID);
        report.setPenaltyPoint(10);
        report.setReviewedAt(LocalDateTime.now());
        reportRepository.save(report);

        // Bước 3: Tái tính điểm uy tín từ DB (report vừa được lưu VALID với penaltyPoint=10 ở trên,
        // nên recalculateReputation() sẽ tự cộng khoản phạt này qua SQL SUM).
        // KHÔNG gọi handleUserViolation() vì sẽ tạo thêm SystemViolationLog — double-counting.
        reviewService.recalculateReputation(authorId);
        log.info("[WRONG-TAG-APPROVE] Da recalculate reputation authorId={}", authorId);

        // Bước 4: Gửi thông báo kỷ luật realtime (lưu DB + WebSocket + FCM push)
        String penaltyMessage = "Bài viết của bạn đã bị hệ thống gỡ bỏ và tài khoản bị trừ 10 điểm uy tín do hành vi cố tình gắn sai thẻ tag sở thích để câu tương tác bừa bãi.";
        try {
            notificationService.createNotificationForReport(
                    authorId, Notification.NotificationType.REPORT_PENALTY, penaltyMessage, null, reportId);
            log.info("[WRONG-TAG-APPROVE] Da gui REPORT_PENALTY notification authorId={}, reportId={}", authorId, reportId);
        } catch (Exception e) {
            log.error("[WRONG-TAG-APPROVE] Gui notification that bai, tiep tuc xu ly reportId={}, authorId={}, error={}",
                    reportId, authorId, e.getMessage(), e);
        }

        log.info("[WRONG-TAG-APPROVE] DONE reportId={}, authorId={}", reportId, authorId);
    }

    // =====================================================================
    // === XỬ LÝ CHẾ TÀI DÂY CHUYỀN KHI HOST BỊ KHÓA TÀI KHOẢN         ===
    // =====================================================================
    //
    // Được gọi sau khi ReputationSanctionService đã khóa tài khoản Host.
    // Phương thức này chạy bất đồng bộ (@Async) trong một transaction riêng
    // để không block luồng chính xử lý lệnh phê duyệt của Admin.
    //
    // PHÂN LOẠI 3 MỐC THỜI GIAN:
    //   Case 1 (Quá khứ)  : now > endTime → GIỮ NGUYÊN lịch sử
    //   Case 2 (Tương lai): now < startTime → HỦY hoạt động + đóng phòng + FCM thành viên
    //   Case 3 (Hiện tại) : startTime ≤ now ≤ endTime → ĐÓNG BĂNG tuyển thành viên mới
    //
    // @param hostId  userId của Host vừa bị hệ thống khóa tài khoản

    @Async
    @Transactional
    public void handleHostSanctionEvent(Long hostId) {
        log.info("[HOST-SANCTION] ▶ START — hostId={}", hostId);

        // Tìm tất cả bài đăng do hostId tạo và chưa bị xóa cứng
        List<Post> hostPosts = postRepository.findByAuthorId(hostId);
        if (hostPosts.isEmpty()) {
            log.info("[HOST-SANCTION] Không tìm thấy bài đăng nào của hostId={}", hostId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("[HOST-SANCTION] Tìm thấy {} bài đăng của hostId={}", hostPosts.size(), hostId);

        for (Post post : hostPosts) {
            // Bỏ qua bài đăng đã bị xóa cứng (archived do báo cáo trước đó)
            if (post.isArchived()) {
                log.debug("[HOST-SANCTION] postId={} đã archived → bỏ qua", post.getId());
                continue;
            }

            try {
                processSinglePost(post, hostId, now);
            } catch (Exception e) {
                // Lỗi 1 post không làm dừng toàn bộ vòng lặp
                log.error("[HOST-SANCTION] ✘ Lỗi khi xử lý postId={}: {}",
                        post.getId(), e.getMessage(), e);
            }
        }

        log.info("[HOST-SANCTION] ■ DONE — hostId={}", hostId);
    }

    // Phân loại và xử lý từng bài đăng theo 3 mốc thời gian
    private void processSinglePost(Post post, Long hostId, LocalDateTime now) {
        Long postId = post.getId();
        LocalDateTime startTime = post.getStartTime();
        LocalDateTime endTime = post.getEndTime();

        // ================================================================
        // CASE 1: HOẠT ĐỘNG ĐÃ KẾT THÚC (Quá khứ)
        // Điều kiện: endTime đã qua HOẶC bài đã cancelled (trạng thái hoàn tất)
        // ================================================================
        boolean isPast = (endTime != null && now.isAfter(endTime)) || post.isCancelled();
        if (isPast) {
            log.info("[HOST-SANCTION] postId={} → Case 1: ĐÃ KẾT THÚC — giữ nguyên lịch sử", postId);
            // Không làm gì — bảo toàn toàn bộ dữ liệu lịch sử tham gia của thành viên
            return;
        }

        // ================================================================
        // CASE 2: HOẠT ĐỘNG SẮP DIỄN RA (Tương lai)
        // Điều kiện: thời điểm hiện tại còn chưa đến startTime
        // ================================================================
        boolean isFuture = (startTime != null && now.isBefore(startTime));
        if (isFuture) {
            log.info("[HOST-SANCTION] postId={} → Case 2: SẮP DIỄN RA — hủy + đóng phòng + FCM", postId);
            handleFuturePost(post, hostId, now);
            return;
        }

        // ================================================================
        // CASE 3: HOẠT ĐỘNG ĐANG DIỄN RA (Hiện tại)
        // Điều kiện: startTime ≤ now ≤ endTime (hoặc startTime null mà endTime chưa qua)
        // ================================================================
        log.info("[HOST-SANCTION] postId={} → Case 3: ĐANG DIỄN RA — đóng băng tuyển thành viên", postId);
        handleOngoingPost(post, hostId, now);
    }

    // ================================================================
    // CASE 2 HANDLER: Hủy hoàn toàn hoạt động sắp diễn ra
    // ================================================================
    private void handleFuturePost(Post post, Long hostId, LocalDateTime now) {
        Long postId = post.getId();

        // Bước 2.1: Đánh dấu bài đăng đã bị hủy bởi hệ thống
        // Dùng cancelled=true để phân biệt với archived (vi phạm nội dung)
        post.setCancelled(true);
        postRepository.save(post);
        log.info("[HOST-SANCTION] postId={} → đã set cancelled=true (CANCELED_BY_SYSTEM)", postId);

        // Bước 2.2: Đóng hoàn toàn phòng chat — các thành viên không thể nhắn tin tiếp
        ChatRoom room = chatRoomRepository.findByPostId(postId).orElse(null);
        if (room != null) {
            room.setActive(false);
            // Dùng label riêng để phân biệt với cancel do host chủ động (CANCELLED)
            room.setInactiveStatusLabel("HOST_LOCKED");
            chatRoomRepository.save(room);
            log.info("[HOST-SANCTION] postId={} → roomId={} đã đóng (HOST_LOCKED)", postId, room.getId());

            // Bước 2.3: Push WebSocket event ACTIVITY_CANCELLED để Android dismiss màn hình chat ngay lập tức
            List<ChatRoomMember> roomMembers = chatRoomMemberRepository.findByRoomId(room.getId());
            for (ChatRoomMember member : roomMembers) {
                messagingTemplate.convertAndSendToUser(
                        member.getUserId().toString(),
                        "/queue/room-events",
                        Map.of("type", "ACTIVITY_CANCELLED", "roomId", room.getId())
                );
            }
        }

        // Bước 2.4: Gửi FCM thông báo hủy đến toàn bộ thành viên đã được APPROVED
        List<Long> approvedUserIds = postMemberRepository.findApprovedUserIdsByPostId(postId);
        log.info("[HOST-SANCTION] postId={} → {} thành viên APPROVED sẽ được thông báo hủy", postId, approvedUserIds.size());

        for (Long memberId : approvedUserIds) {
            if (memberId.equals(hostId)) continue; // Bỏ qua Host chính (đang bị khóa)

            try {
                User member = userRepository.findById(memberId).orElse(null);
                if (member == null) continue;

                // Lưu notification vào DB + WebSocket (không FCM để gửi riêng bên dưới)
                notificationService.createNotificationWithoutPush(
                        memberId,
                        Notification.NotificationType.ACTIVITY_CANCELLED,
                        "Hoạt động \"" + truncate(post.getContent(), 40) + "\" đã bị hủy do tài khoản "
                                + "người tổ chức vi phạm tiêu chuẩn cộng đồng. Chúng tôi xin lỗi vì sự bất tiện này.",
                        "WeConnect");

                // FCM push để thông báo khi app đang tắt màn hình
                if (member.getFcmToken() != null && !member.getFcmToken().isBlank()) {
                    Map<String, String> data = new HashMap<>();
                    data.put("type", "ACTIVITY_CANCELLED");
                    data.put("postId", String.valueOf(postId));
                    fcmService.sendNotification(
                            member.getFcmToken(),
                            "🚫 Hoạt động đã bị hủy",
                            "Hoạt động bạn đã đăng ký tham gia vừa bị hủy do tổ chức vi phạm quy định.",
                            data);
                }
            } catch (Exception e) {
                log.error("[HOST-SANCTION] Gửi thông báo hủy cho memberId={} thất bại: {}", memberId, e.getMessage());
            }
        }
    }

    // ================================================================
    // CASE 3 HANDLER: Đóng băng tuyển thành viên, giữ nguyên hoạt động
    // ================================================================
    private void handleOngoingPost(Post post, Long hostId, LocalDateTime now) {
        Long postId = post.getId();

        // Bước 3.1: GIỮ NGUYÊN trạng thái bài đăng và KHÔNG đóng phòng chat.
        // Các thành viên APPROVED vẫn dùng phòng chat để phục vụ buổi gặp mặt thực tế.
        // Phòng chat chủ động KHÔNG gọi room.setActive(false).

        // Bước 3.2: Tước quyền nhắn tin của Host → được xử lý tại
        //           ChatService.sendMessage() bằng cách kiểm tra trạng thái tài khoản Host.
        //           (Xem phần "Kiểm tra Host bị khóa trong phòng hoạt động" tại ChatService)
        log.info("[HOST-SANCTION] postId={} → Bước 3.2: Host sẽ bị chặn tại ChatService.sendMessage()", postId);

        // Bước 3.3: Tự động từ chối tất cả yêu cầu PENDING → REJECTED_BY_SYSTEM
        int rejectedCount = postMemberRepository.rejectAllPendingByPostId(postId);
        log.info("[HOST-SANCTION] postId={} → Bước 3.3: {} yêu cầu PENDING → REJECTED_BY_SYSTEM", postId, rejectedCount);

        // Bước 3.4: Chèn tin nhắn hệ thống vào phòng chat realtime
        ChatRoom room = chatRoomRepository.findByPostId(postId).orElse(null);
        if (room != null) {
            String warningMessage =
                    "⚠️ Cảnh báo bảo mật: Tài khoản của người tổ chức hoạt động này vừa bị hệ thống " +
                    "khóa do vi phạm tiêu chuẩn cộng đồng. Hoạt động hiện tại tạm dừng nhận thành viên " +
                    "mới. Các thành viên có mặt vui lòng cẩn trọng khi tương tác và tự bảo vệ thông tin " +
                    "cá nhân.";

            // Lưu vào DB với type="SYSTEM" và broadcast realtime qua STOMP /topic/chat/{roomId}
            ChatMessage sysMsg = ChatMessage.builder()
                    .roomId(room.getId())
                    .senderId(0L)           // senderId=0 → đây là tin nhắn hệ thống
                    .content(warningMessage)
                    .type("SYSTEM")
                    .build();
            ChatMessage savedMsg = chatMessageRepository.save(sysMsg);
            log.info("[HOST-SANCTION] postId={} → roomId={} → Đã lưu SYSTEM message id={}", postId, room.getId(), savedMsg.getId());

            // Broadcast tới tất cả client đang subscribe /topic/chat/{roomId}
            // Dùng ChatMessageResponse DTO (không dùng Map) để tránh overload ambiguity
            ChatMessageResponse sysResponse = ChatMessageResponse.builder()
                    .id(savedMsg.getId())
                    .roomId(savedMsg.getRoomId())
                    .senderId(0L)
                    .senderName("")
                    .content(savedMsg.getContent())
                    .type("SYSTEM")
                    .sentByCurrentUser(false)
                    .createdAt(savedMsg.getCreatedAt())
                    .build();
            messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), sysResponse);

            // Cập nhật lastMessage cho từng member (để chat-list hiển thị tin nhắn mới)
            List<ChatRoomMember> roomMembers = chatRoomMemberRepository.findByRoomId(room.getId());
            for (ChatRoomMember member : roomMembers) {
                messagingTemplate.convertAndSendToUser(
                        member.getUserId().toString(),
                        "/queue/chat-list",
                        Map.of("roomId", room.getId())
                );
            }
        } else {
            log.warn("[HOST-SANCTION] postId={} → Không tìm thấy ChatRoom → bỏ qua Bước 3.4", postId);
        }

        // Bước 3.5 (Tùy chọn): Gửi FCM thông báo cho thành viên APPROVED về sự việc
        List<Long> approvedUserIds = postMemberRepository.findApprovedUserIdsByPostId(postId);
        for (Long memberId : approvedUserIds) {
            if (memberId.equals(hostId)) continue;
            try {
                User member = userRepository.findById(memberId).orElse(null);
                if (member == null || member.getFcmToken() == null || member.getFcmToken().isBlank()) continue;
                Map<String, String> data = new HashMap<>();
                data.put("type", "HOST_LOCKED_WARNING");
                data.put("postId", String.valueOf(postId));
                fcmService.sendNotification(
                        member.getFcmToken(),
                        "⚠️ Cảnh báo bảo mật từ WeConnect",
                        "Người tổ chức hoạt động bạn đang tham gia vừa bị hệ thống khóa tài khoản. "
                                + "Vui lòng cẩn trọng.",
                        data);
            } catch (Exception e) {
                log.error("[HOST-SANCTION] FCM warning cho memberId={} thất bại: {}", memberId, e.getMessage());
            }
        }
    }

    // Cắt ngắn chuỗi, tránh NPE
    private String truncate(String s, int maxLen) {
        if (s == null) return "hoạt động";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
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
            } else if (r.getTargetType() == Report.TargetType.REVIEW) {
                UserReview reviewObj = userReviewRepository.findById(r.getTargetId()).orElse(null);
                if (reviewObj != null) {
                    String snippet = reviewObj.getComment() != null
                            ? (reviewObj.getComment().length() > 40
                                ? reviewObj.getComment().substring(0, 40) + "..." : reviewObj.getComment())
                            : "(không có nội dung)";
                    map.put("targetName", "Nhận xét: \"" + snippet + "\"");
                } else {
                    map.put("targetName", "Nhận xét (đã xóa)");
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

        // REVIEW: kẻ viết nhận xét vu khống xem thông tin bị phạt của mình.
        // Review đã bị xóa sau khi admin phê duyệt → không thể dùng resolveTargetUserId().
        // Case 1 (report mới): violatorUserId được gán khi approve.
        // Case 2 (report cũ trước khi có violatorUserId): fallback kiểm tra bảng notifications.
        if (report.getTargetType() == Report.TargetType.REVIEW) {
            boolean isViolator = (report.getViolatorUserId() != null && userId.equals(report.getViolatorUserId()))
                    || notificationRepository.existsByUserIdAndRelatedReportIdAndType(
                            userId, reportId, Notification.NotificationType.REPORT_PENALTY);
            if (isViolator) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", report.getId());
                map.put("targetType", "REVIEW");
                map.put("reason", report.getReason());
                map.put("status", report.getStatus().name());
                map.put("penaltyPoint", report.getPenaltyPoint());
                map.put("adminAction", report.getAdminAction());
                map.put("reviewedAt", report.getReviewedAt());
                User violator = userRepository.findById(userId).orElse(null);
                map.put("currentReputationScore", violator != null ? violator.getReputationScore() : null);
                return map;
            }
        }

        Long targetUserId = resolveTargetUserId(report);
        if (targetUserId == null) {
            throw new RuntimeException("Nội dung liên quan đến báo cáo này đã bị xóa hoặc không còn tồn tại.");
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

    public Map<String, Object> getReporterReportDetail(Long reportId, Long userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy báo cáo."));

        if (!userId.equals(report.getReporterId())) {
            throw new RuntimeException("Bạn không có quyền xem báo cáo này.");
        }

        Long targetUserId = resolveTargetUserId(report);
        User targetUser = targetUserId != null ? userRepository.findById(targetUserId).orElse(null) : null;
        String targetUserName = displayName(targetUser);

        Map<String, Object> map = new HashMap<>();
        map.put("id", report.getId());
        map.put("targetType", report.getTargetType().name());
        map.put("targetUserName", targetUserName);
        map.put("reason", report.getReason());
        map.put("status", report.getStatus().name());
        map.put("penaltyPoint", report.getPenaltyPoint() != null ? report.getPenaltyPoint() : 0);
        map.put("adminAction", report.getAdminAction());
        map.put("reviewedAt", report.getReviewedAt());

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
        if (report.getTargetType() == Report.TargetType.REVIEW) {
            return userReviewRepository.findById(report.getTargetId())
                    .map(UserReview::getReviewedUserId)
                    .orElse(null);
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
        map.put("detailReason", r.getDetailReason());
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
        } else if (r.getTargetType() == Report.TargetType.REVIEW) {
            UserReview review = userReviewRepository.findById(r.getTargetId()).orElse(null);
            if (review != null) {
                Map<String, Object> targetInfo = new HashMap<>();
                targetInfo.put("rating", review.getRating());
                targetInfo.put("comment", review.getComment());
                targetInfo.put("activityName", review.getActivityName());
                targetInfo.put("reputationLabel", review.getReputationLabel());

                // Nạn nhân (người bị nhận xét xấu)
                targetInfo.put("reviewedUserId", review.getReviewedUserId());
                User reviewedUser = userRepository.findById(review.getReviewedUserId()).orElse(null);
                String reviewedName = reviewedUser != null ? reviewedUser.getFullName() : "Unknown";
                targetInfo.put("reviewedUserName", reviewedName);
                targetInfo.put("reviewedUserAvatarUrl", reviewedUser != null ? reviewedUser.getAvatarUrl() : null);
                targetInfo.put("reviewedUserReputationScore", reviewedUser != null ? reviewedUser.getReputationScore() : null);

                // Kẻ viết nhận xét vu khống
                targetInfo.put("reviewerId", review.getReviewerId());
                User reviewer = userRepository.findById(review.getReviewerId()).orElse(null);
                targetInfo.put("reviewerName", reviewer != null ? reviewer.getFullName() : "Unknown");
                targetInfo.put("reviewerAvatarUrl", reviewer != null ? reviewer.getAvatarUrl() : null);
                targetInfo.put("reviewerReputationScore", reviewer != null ? reviewer.getReputationScore() : null);
                targetInfo.put("reviewerViolationCount", reviewer != null ? reviewer.getViolationCount() : null);

                String snippet = review.getComment() != null
                        ? (review.getComment().length() > 60 ? review.getComment().substring(0, 60) + "..." : review.getComment())
                        : "";
                map.put("targetName", "Nhận xét của " + (reviewer != null ? reviewer.getFullName() : "Unknown") + " về " + reviewedName + ": \"" + snippet + "\"");
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
