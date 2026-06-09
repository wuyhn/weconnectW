package com.weconnect.backend.service;

import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Dịch vụ tập trung xử lý chế tài tài khoản dựa trên điểm uy tín.
 *
 * Trước đây logic này bị phân tán ở hai nơi:
 *   - ReportService.applyReputationSanction()
 *   - UserService.applyZeroReputationSanction()
 * Gộp về đây để đảm bảo mọi luồng đều dùng cùng một implementation.
 *
 * IDEMPOTENCY GUARD: chỉ áp chế tài khi tài khoản đang ACTIVE.
 * Nếu đã LOCKED_TEMP hoặc BANNED thì bỏ qua, tránh tăng violationCount
 * mỗi lần recalculateReputation() được gọi lại (ví dụ khi có review mới).
 */
@Service
public class ReputationSanctionService {

    private static final Logger log = LoggerFactory.getLogger(ReputationSanctionService.class);

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FCMService fcmService;
    private final SimpMessagingTemplate messagingTemplate;

    public ReputationSanctionService(UserRepository userRepository,
                                     NotificationService notificationService,
                                     FCMService fcmService,
                                     SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.fcmService = fcmService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Admin chủ động khóa tài khoản người dùng (không qua luồng reputation/báo cáo).
     * Không tăng violationCount — đây là hành động hành chính trực tiếp của Admin.
     * Gửi WebSocket ACCOUNT_LOCKED + FCM FORCE_LOGOUT như luồng khóa bình thường.
     *
     * @param userId ID người dùng cần khóa
     * @return true nếu đã khóa thành công, false nếu tài khoản đã bị khóa/banned trước đó
     */
    @Transactional
    public boolean lockAccountByAdmin(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[ADMIN-LOCK] Không tìm thấy userId={}", userId);
            return false;
        }

        // Bỏ qua nếu tài khoản đã bị chế tài (idempotency)
        if (!User.STATUS_ACTIVE.equals(user.getStatus())) {
            log.info("[ADMIN-LOCK] Tài khoản userId={} đã có status={} — bỏ qua", userId, user.getStatus());
            return false;
        }

        // Khóa 7 ngày: set LOCKED_TEMP, lockUntil, isBlocked — không tăng violationCount
        LocalDateTime unlockAt = LocalDateTime.now().plusDays(7);
        user.setStatus(User.STATUS_LOCKED_TEMP);
        user.setLockUntil(unlockAt);
        user.setBlocked(true);
        userRepository.save(user);

        String msg = "Tài khoản của bạn đã bị Admin khóa tạm thời 7 ngày. "
                + "Tài khoản sẽ tự động mở khóa vào ngày " + unlockAt.toLocalDate() + ".";
        sendDbNotification(user, Notification.NotificationType.ADMIN_ACTION, msg);

        // Kick online user qua WebSocket
        sendAccountLockedViaWebSocket(user, unlockAt);
        // Fallback FCM khi user đang ở background
        sendForceLogoutFcm(user, unlockAt);

        log.warn("[ADMIN-LOCK] Admin đã khóa userId={} đến {}", userId, unlockAt);
        return true;
    }

    /**
     * Admin mở khóa tài khoản: reset trạng thái về ACTIVE.
     */
    @Transactional
    public void unlockAccountByAdmin(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        user.setStatus(User.STATUS_ACTIVE);
        user.setLockUntil(null);
        user.setBlocked(false);
        userRepository.save(user);
        log.info("[ADMIN-UNLOCK] Admin đã mở khóa userId={}", userId);
    }

    /**
     * Áp dụng chế tài trạng thái tài khoản dựa trên điểm uy tín vừa được tính lại.
     *
     * - score > 0  → không làm gì; caller tự gửi notification cảnh báo cụ thể (trừ X điểm, vi phạm gì).
     * - score <= 0, user đang ACTIVE → tăng violationCount rồi LOCKED_TEMP hoặc BANNED.
     * - score <= 0, user đã bị chế tài → bỏ qua (idempotency guard).
     *
     * @param userId       ID người dùng vừa được recalculate
     * @param currentScore Điểm uy tín mới (đã clamp [0, 100])
     */
    @Transactional
    public void applySanctionBasedOnScore(Long userId, double currentScore) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[SANCTION] Không tìm thấy userId={} — bỏ qua", userId);
            return;
        }

        // IDEMPOTENCY GUARD: chỉ áp chế tài khi tài khoản đang hoàn toàn bình thường.
        // Tránh việc mỗi lần recalculate (ví dụ user nhận được 1 review mới trong khi
        // score vẫn = 0) lại tăng violationCount thêm 1 lần nữa.
        if (!User.STATUS_ACTIVE.equals(user.getStatus())) {
            log.info("[SANCTION] Bỏ qua — tài khoản đã bị chế tài userId={}, status={}",
                    userId, user.getStatus());
            return;
        }

        if (currentScore > 0) {
            // Điểm còn dương: không có thay đổi trạng thái.
            // Caller (ReportService / UserService) tự gửi notification cảnh báo cụ thể.
            log.info("[SANCTION] score={} > 0, không cần chế tài userId={}", currentScore, userId);
            return;
        }

        // Điểm về 0: áp chế tài, tăng violationCount
        int newViolationCount = user.getViolationCount() + 1;
        user.setViolationCount(newViolationCount);
        user.setReputationScore(0);
        user.setBlocked(true);

        log.warn("[SANCTION] Điểm về 0 userId={} — violationCount mới={}", userId, newViolationCount);

        if (newViolationCount >= 3) {
            applyPermanentBan(user, newViolationCount);
        } else {
            applyTemporaryLock(user, newViolationCount);
        }
    }

    // ============================================================
    //  Private: Khóa vĩnh viễn (BANNED)
    // ============================================================

    private void applyPermanentBan(User user, int violationCount) {
        user.setStatus(User.STATUS_BANNED);
        user.setLockUntil(null);
        userRepository.save(user);

        String msg = "Tài khoản của bạn đã bị khóa vĩnh viễn do tích lũy "
                + violationCount + " lần vi phạm nghiêm trọng tiêu chuẩn cộng đồng WeConnect. "
                + "Mọi thắc mắc vui lòng liên hệ bộ phận hỗ trợ.";
        sendDbNotification(user, Notification.NotificationType.ADMIN_ACTION, msg);

        sendFcmSafely(user,
                "🚫 TÀI KHOẢN BỊ KHÓA VĨNH VIỄN",
                "Tài khoản của bạn đã bị tước quyền truy cập vĩnh viễn khỏi hệ sinh thái WeConnect "
                        + "do vi phạm nghiêm trọng và lặp lại (lần vi phạm thứ " + violationCount + ").",
                "ACCOUNT_BANNED");

        log.warn("[SANCTION] BANNED userId={}, violationCount={}", user.getId(), violationCount);
    }

    // ============================================================
    //  Private: Khóa tạm thời 7 ngày (LOCKED_TEMP)
    // ============================================================

    private void applyTemporaryLock(User user, int violationCount) {
        LocalDateTime unlockAt = LocalDateTime.now().plusDays(7);
        user.setStatus(User.STATUS_LOCKED_TEMP);
        user.setLockUntil(unlockAt);
        userRepository.save(user);

        String msg = "Tài khoản của bạn bị khóa tạm thời 7 ngày do điểm uy tín về 0 "
                + "(vi phạm lần " + violationCount + "). "
                + "Tài khoản sẽ tự động mở khóa vào ngày " + unlockAt.toLocalDate() + ".";
        sendDbNotification(user, Notification.NotificationType.ADMIN_ACTION, msg);

        // Gửi notification tray thông thường (dành cho user không đang dùng app)
        sendFcmSafely(user,
                "🔒 TÀI KHOẢN BỊ KHÓA TẠM THỜI",
                "Điểm uy tín của bạn về 0 do vi phạm lần " + violationCount
                        + ". Tài khoản bị khóa tạm thời 7 ngày. "
                        + "Ngày mở khóa tự động: " + unlockAt.toLocalDate() + ".",
                "ACCOUNT_LOCKED_TEMP");

        // Kịch bản 1: Push real-time qua WebSocket để kick-out ngay user đang online.
        // Nếu user đang kết nối STOMP, Android nhận ngay → hiển thị AlertDialog → đăng xuất.
        // FCM (sendForceLogoutFcm) giữ lại làm fallback khi user chạy app ở background.
        sendAccountLockedViaWebSocket(user, unlockAt);
        sendForceLogoutFcm(user, unlockAt);

        log.warn("[SANCTION] LOCKED_TEMP userId={}, violationCount={}, unlockAt={}",
                user.getId(), violationCount, unlockAt);
    }

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Push sự kiện ACCOUNT_LOCKED trực tiếp qua STOMP WebSocket đến đúng user đang online.
     * Principal = userId.toString() (xem WebSocketAuthInterceptor).
     * Android subscribeToAccountStatus() lắng nghe /user/queue/account-status.
     *
     * Payload gồm action, message, và lockUntil (ISO-8601) để Android hiển thị đúng ngày.
     */
    private void sendAccountLockedViaWebSocket(User user, LocalDateTime unlockAt) {
        try {
            Map<String, String> payload = Map.of(
                    "action", "ACCOUNT_LOCKED",
                    "message", "Tài khoản của bạn đã bị khóa tạm thời 7 ngày do vi phạm tiêu chuẩn cộng đồng!",
                    "lockUntil", unlockAt.format(ISO_FMT)
            );
            messagingTemplate.convertAndSendToUser(
                    user.getId().toString(),
                    "/queue/account-status",
                    payload
            );
            log.info("[SANCTION-WS] Đã push ACCOUNT_LOCKED qua WebSocket đến userId={}", user.getId());
        } catch (Exception e) {
            log.warn("[SANCTION-WS] Gửi WebSocket thất bại userId={}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Gửi FCM data-only (không notification) với action=FORCE_LOGOUT — fallback khi background.
     * lockUntil được đưa vào payload để Android hiển thị ngày mở khóa chính xác.
     */
    private void sendForceLogoutFcm(User user, LocalDateTime unlockAt) {
        String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.warn("[SANCTION-FCM] userId={} không có fcmToken, bỏ qua FORCE_LOGOUT push.", user.getId());
            return;
        }
        try {
            fcmService.sendDataOnlyMessage(token, Map.of(
                    "action", "FORCE_LOGOUT",
                    "message", "Tài khoản của bạn đã bị khóa tạm thời 7 ngày do vi phạm tiêu chuẩn cộng đồng!",
                    "lockUntil", unlockAt.format(ISO_FMT)
            ));
            log.info("[SANCTION-FCM] Đã gửi FORCE_LOGOUT FCM đến userId={}", user.getId());
        } catch (Exception e) {
            log.error("[SANCTION-FCM] Gửi FORCE_LOGOUT thất bại userId={}: {}", user.getId(), e.getMessage());
        }
    }

    // ============================================================
    //  Private helpers
    // ============================================================

    private void sendDbNotification(User user, Notification.NotificationType type, String message) {
        try {
            // createNotificationWithoutPush: lưu DB + đẩy WebSocket; FCM gửi riêng bên dưới
            notificationService.createNotificationWithoutPush(user.getId(), type, message, "WeConnect");
        } catch (Exception e) {
            log.error("[SANCTION] Lưu DB notification thất bại userId={}: {}", user.getId(), e.getMessage());
        }
    }

    private void sendFcmSafely(User user, String title, String body, String type) {
        String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.warn("[SANCTION-FCM] userId={} không có fcmToken, bỏ qua push. type={}", user.getId(), type);
            return;
        }
        try {
            fcmService.sendNotification(token, title, body, Map.of("type", type));
        } catch (Exception e) {
            log.error("[SANCTION-FCM] Gửi FCM thất bại userId={}, type={}: {}",
                    user.getId(), type, e.getMessage());
        }
    }
}
