package com.weconnect.backend.service;

import com.weconnect.backend.dto.ChangePasswordRequest;
import com.weconnect.backend.dto.UpdateProfileRequest;
import com.weconnect.backend.dto.UserProfileResponse;
import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(UserService.class.getName());

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;
    private final FriendshipRepository friendshipRepository;
    private final NotificationRepository notificationRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserReviewRepository userReviewRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ReviewService reviewService;
    private final NotificationService notificationService;
    private final FCMService fcmService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PostRepository postRepository, PostMemberRepository postMemberRepository,
                       FriendshipRepository friendshipRepository, NotificationRepository notificationRepository,
                       ChatRoomMemberRepository chatRoomMemberRepository, UserReviewRepository userReviewRepository,
                       BlockedUserRepository blockedUserRepository, ChatMessageRepository chatMessageRepository,
                       ReviewService reviewService, NotificationService notificationService,
                       FCMService fcmService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
        this.friendshipRepository = friendshipRepository;
        this.notificationRepository = notificationRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.userReviewRepository = userReviewRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.reviewService = reviewService;
        this.notificationService = notificationService;
        this.fcmService = fcmService;
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        return toProfileResponse(user);
    }

    public UserProfileResponse getProfile(Long userId, Long currentUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        return toProfileResponse(user, currentUserId);
    }

    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getBirthday() != null) user.setBirthday(request.getBirthday());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getBio() != null) user.setBio(request.getBio());
        if (request.getInterestTags() != null) user.setInterestTags(request.getInterestTags());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        userRepository.save(user);
        return toProfileResponse(user);
    }

    public String changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return "Đổi mật khẩu thành công!";
    }

    @Transactional
    public void handleUserViolation(Long userId, int penaltyPoints) {
        handleUserViolationInternal(userId, penaltyPoints, true, null, null, "AUTO_VIOLATION");
    }

    @Transactional
    public void handleApprovedReportViolation(Long userId, int penaltyPoints, String notificationTitle,
                                              String notificationBody, String source) {
        // Bao cao da luu penaltyPoint trong bang reports, ReviewService se tru diem nay khi recalculate.
        // Khong cong vao violationPenaltySum de tranh tru diem hai lan sau khi server tinh lai reputation.
        handleUserViolationInternal(userId, penaltyPoints, false, notificationTitle, notificationBody, source);
    }

    private void handleUserViolationInternal(Long userId, int penaltyPoints, boolean addToViolationPenaltySum,
                                             String notificationTitle, String notificationBody, String source) {
        if (penaltyPoints <= 0) {
            throw new IllegalArgumentException("So diem phat phai lon hon 0.");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay nguoi dung."));

        logger.info("[FCM-VIOLATION] Bat dau xu ly vi pham userId=" + userId
                + " | penaltyPoints=" + penaltyPoints
                + " | addToViolationPenaltySum=" + addToViolationPenaltySum
                + " | source=" + source
                + " | reputationScore hien tai=" + user.getReputationScore()
                + " | fcmToken=" + (user.getFcmToken() == null ? "NULL" : user.getFcmToken().isEmpty() ? "EMPTY" : "OK"));

        if (addToViolationPenaltySum) {
            user.setViolationPenaltySum(safeAdd(user.getViolationPenaltySum(), penaltyPoints));
        }

        String resolvedNotificationTitle = hasText(notificationTitle)
                ? notificationTitle
                : "⚠️ Cảnh báo vi phạm quy chuẩn uy tín";
        String resolvedNotificationBody = hasText(notificationBody)
                ? notificationBody
                : "Tài khoản của bạn vừa bị trừ " + penaltyPoints
                + " điểm uy tín do vi phạm quy chuẩn cộng đồng.";

        // ==================== TRUONG HOP 1: Diem con > 0 ====================
        double scoreAfterPenalty = user.getReputationScore() - penaltyPoints;
        if (scoreAfterPenalty > 0) {
            user.setReputationScore(scoreAfterPenalty);
            userRepository.save(user);

            String dbMsg = "Tài khoản của bạn bị trừ " + penaltyPoints
                    + " điểm uy tín do vi phạm quy chuẩn. Điểm còn lại: "
                    + Math.round(scoreAfterPenalty) + ".";
            createSanctionNotification(user, Notification.NotificationType.ADMIN_WARNING, dbMsg);

            if (hasText(notificationTitle) || hasText(notificationBody)) {
                // Luong approve report can title/body rieng de nguoi bi bao cao nhan dung noi dung Admin da xac nhan vi pham.
                sendDirectFcmSafely(user, resolvedNotificationTitle,
                        resolvedNotificationBody + " So diem hien tai cua ban con lai: "
                                + Math.round(scoreAfterPenalty) + " diem.",
                        "TH1-REPORT-APPROVED");
                return;
            }

            // --- Gui Push Notification FCM ---
            String fcmToken = user.getFcmToken();
            if (fcmToken == null || fcmToken.isBlank()) {
                logger.warning("[FCM-VIOLATION] CANH BAO: userId=" + userId
                        + " khong co fcmToken — bo qua viec gui push notification (TH1).");
            } else {
                String title = "⚠️ Cảnh báo vi phạm quy chuẩn uy tín";
                String body  = "Tài khoản của bạn vừa bị trừ " + penaltyPoints
                        + " điểm uy tín. Số điểm hiện tại của bạn còn lại: "
                        + Math.round(scoreAfterPenalty)
                        + " điểm. Vui lòng chú ý tuân thủ tiêu chuẩn cộng đồng để tránh bị khóa tài khoản.";
                logger.info("[FCM-VIOLATION] Chuan bi goi fcmService.sendNotification (TH1) cho userId=" + userId
                        + " | title=" + title);
                fcmService.sendNotification(fcmToken, title, body, null);
                logger.info("[FCM-VIOLATION] Da goi fcmService.sendNotification (TH1) thanh cong cho userId=" + userId);
            }
            return;
        }

        if (hasText(notificationTitle) || hasText(notificationBody)) {
            // Diem ve 0 van phai gui thong bao xac nhan bi tru diem truoc khi gui thong bao khoa tai khoan.
            user.setReputationScore(0);
            userRepository.save(user);
            createSanctionNotification(user, Notification.NotificationType.ADMIN_WARNING, resolvedNotificationBody);
            sendDirectFcmSafely(user, resolvedNotificationTitle, resolvedNotificationBody, "TH2-REPORT-PENALTY-CONFIRMED");
        }

        applyZeroReputationSanction(user, source);
    }

    @Transactional
    public boolean applyZeroReputationSanctionIfNeeded(Long userId, String source) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay nguoi dung."));

        if (user.getReputationScore() > 0) {
            logger.info("[REPUTATION-SANCTION] Bo qua vi diem uy tin van > 0 userId=" + userId
                    + " | reputationScore=" + user.getReputationScore()
                    + " | source=" + source);
            return false;
        }

        if (User.STATUS_LOCKED_TEMP.equals(user.getStatus()) || User.STATUS_BANNED.equals(user.getStatus())) {
            logger.info("[REPUTATION-SANCTION] Bo qua vi tai khoan da bi che tai userId=" + userId
                    + " | status=" + user.getStatus()
                    + " | source=" + source);
            return false;
        }

        applyZeroReputationSanction(user, source);
        return true;
    }

    private void applyZeroReputationSanction(User user, String source) {
        Long userId = user.getId();
        int violationCount = safeAdd(user.getViolationCount(), 1);
        user.setReputationScore(0);
        user.setViolationCount(violationCount);
        user.setBlocked(true);

        logger.info("[REPUTATION-SANCTION] Diem ve 0 - violationCount moi=" + violationCount
                + " userId=" + userId
                + " | source=" + source);

        if (violationCount >= 3) {
            user.setStatus(User.STATUS_BANNED);
            user.setLockUntil(null);
            userRepository.save(user);

            String dbMsg = "Tài khoản đã bị khóa vĩnh viễn do vi phạm nghiêm trọng tiêu chuẩn cộng đồng quá 3 lần.";
            createSanctionNotification(user, Notification.NotificationType.ADMIN_ACTION, dbMsg);

            String title = "🚫 TÀI KHOẢN BỊ KHÓA VĨNH VIỄN";
            String body  = "Tài khoản của bạn đã bị khóa vĩnh viễn khỏi hệ sinh thái WeConnect"
                    + " do tích lũy số lần vi phạm nghiêm trọng quá 3 lần."
                    + " Mọi quyền truy cập của bạn đã bị từ chối.";
            sendDirectFcmSafely(user, title, body, "TH2.2-BANNED");
            return;
        }

        user.setStatus(User.STATUS_LOCKED_TEMP);
        user.setLockUntil(LocalDateTime.now().plusDays(7));
        userRepository.save(user);

        String dbMsg = "Tài khoản của bạn bị tạm khóa 7 ngày do hết điểm uy tín (Vi phạm lần thứ "
                + violationCount + ").";
        createSanctionNotification(user, Notification.NotificationType.ADMIN_ACTION, dbMsg);

        String title = "⏳ Tài khoản của bạn đã bị KHÓA TẠM THỜI";
        String body  = "Điểm uy tín của bạn đã rơi về mức 0. Tài khoản chính thức bị khóa tạm thời"
                + " trong vòng 7 ngày (Vi phạm lần thứ " + violationCount + ")."
                + " Hệ thống sẽ tự động khôi phục sau khi hết thời hạn phạt.";
        sendDirectFcmSafely(user, title, body, "TH2.1-LOCKED_TEMP");
    }

    private void sendDirectFcmSafely(User user, String title, String body, String context) {
        Long userId = user.getId();
        String fcmToken = user.getFcmToken();
        if (fcmToken == null || fcmToken.isBlank()) {
            logger.warning("[FCM-VIOLATION] CANH BAO: userId=" + userId
                    + " khong co fcmToken - bo qua viec gui push notification (" + context + ").");
            return;
        }

        try {
            logger.info("[FCM-VIOLATION] Chuan bi goi fcmService.sendNotification (" + context + ") cho userId=" + userId
                    + " | title=" + title);
            fcmService.sendNotification(fcmToken, title, body, null);
            logger.info("[FCM-VIOLATION] Da goi fcmService.sendNotification (" + context + ") thanh cong cho userId=" + userId);
        } catch (Exception e) {
            logger.warning("[FCM-VIOLATION] Gui push notification that bai userId=" + userId
                    + " | context=" + context
                    + " | error=" + e.getMessage());
        }
    }

    @Transactional
    public void restoreExpiredTemporaryLock(User user) {
        // Dong bo voi AuthService.login(): het han khoa tam thi mo khoa va cap lai san 30 diem.
        // Khong recalculate o day vi penalty cu co the keo reputationScore ve 0 ngay sau khi mo khoa.
        user.setStatus(User.STATUS_ACTIVE);
        user.setBlocked(false);
        user.setLockUntil(null);
        user.setReputationScore(30);
        user.setViolationPenaltySum(Math.max(0, user.getViolationPenaltySum() - 30));
        userRepository.save(user);
    }

    /**
     * Xóa tài khoản và tất cả dữ liệu liên quan.
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        // 1. Xóa tất cả bài đăng của user (và members của từng bài)
        List<Post> userPosts = postRepository.findByAuthorId(userId);
        for (Post post : userPosts) {
            postMemberRepository.deleteByPostId(post.getId());
            postRepository.delete(post);
        }

        // 2. Xóa tất cả memberships (user tham gia bài của người khác)
        postMemberRepository.findByUserId(userId).forEach(postMemberRepository::delete);

        // 3. Xóa tất cả friendships
        friendshipRepository.findBySenderIdOrReceiverId(userId, userId)
                .forEach(friendshipRepository::delete);

        // 4. Xóa tất cả notifications
        notificationRepository.findByUserId(userId).forEach(notificationRepository::delete);

        // 5. Xóa tất cả chat room memberships
        chatRoomMemberRepository.findByUserId(userId).forEach(chatRoomMemberRepository::delete);

        // 6. Xóa tất cả reviews (là reviewer hoặc được review)
        userReviewRepository.findByReviewerIdOrReviewedUserId(userId, userId)
                .forEach(userReviewRepository::delete);

        // 7. Xóa tất cả blocked records
        blockedUserRepository.findByBlockerIdOrBlockedId(userId, userId)
                .forEach(blockedUserRepository::delete);

        // 8. Xóa user
        userRepository.delete(user);
    }

    public UserProfileResponse toProfileResponse(User user) {
        return toProfileResponse(user, null);
    }

    public UserProfileResponse toProfileResponse(User user, Long currentUserId) {
        boolean isBlockedByMe = false;
        boolean hasBlockedMe = false;
        if (currentUserId != null && user.getId() != null && !currentUserId.equals(user.getId())) {
            isBlockedByMe = blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, user.getId());
            hasBlockedMe = blockedUserRepository.existsByBlockerIdAndBlockedId(user.getId(), currentUserId);
        }
        boolean blockedBetweenUsers = isBlockedByMe || hasBlockedMe;
        int totalReviewCount = userReviewRepository.countByReviewedUserId(user.getId());

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .birthday(blockedBetweenUsers ? null : user.getBirthday())
                .gender(blockedBetweenUsers ? null : user.getGender())
                .avatarUrl(user.getAvatarUrl())
                .bio(blockedBetweenUsers ? null : user.getBio())
                .interestTags(blockedBetweenUsers ? null : user.getInterestTags())
                .averageRating(blockedBetweenUsers ? 0 : user.getAverageRating())
                .reputationScore(blockedBetweenUsers ? 0 : user.getReputationScore())
                .totalReviewCount(totalReviewCount)
                .isActivityJoinLocked(user.isBlocked())
                .isBlockedByMe(isBlockedByMe)
                .hasBlockedMe(hasBlockedMe)
                .isBlockedBetweenUsers(blockedBetweenUsers)
                .build();
    }

    public void appendBlockStatus(Map<String, Object> item, Long currentUserId, Long targetUserId) {
        boolean isBlockedByMe = false;
        boolean hasBlockedMe = false;
        if (currentUserId != null && targetUserId != null && !currentUserId.equals(targetUserId)) {
            isBlockedByMe = blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, targetUserId);
            hasBlockedMe = blockedUserRepository.existsByBlockerIdAndBlockedId(targetUserId, currentUserId);
        }
        item.put("isBlockedByMe", isBlockedByMe);
        item.put("hasBlockedMe", hasBlockedMe);
        item.put("isBlockedBetweenUsers", isBlockedByMe || hasBlockedMe);
    }

    /**
     * Gợi ý user có sở thích chung, sắp xếp theo số sở thích chung giảm dần.
     * Loại trừ chính mình và user đang xem (excludeId).
     */
    public List<Map<String, Object>> getSuggestedUsers(Long currentUserId, Long excludeId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        String myTags = currentUser.getInterestTags();
        if (myTags == null || myTags.isEmpty()) return Collections.emptyList();

        Set<String> myInterests = Arrays.stream(myTags.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (myInterests.isEmpty()) return Collections.emptyList();

        List<User> allUsers = userRepository.findAll();
        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (User u : allUsers) {
            // Loại trừ chính mình
            if (u.getId().equals(currentUserId)) continue;
            // Loại trừ user đang xem
            if (excludeId != null && u.getId().equals(excludeId)) continue;
            // Loại trừ users đã chặn tôi hoặc tôi đã chặn
            if (blockedUserRepository.existsByBlockerIdAndBlockedId(u.getId(), currentUserId)) continue;
            if (blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, u.getId())) continue;

            String theirTags = u.getInterestTags();
            if (theirTags == null || theirTags.isEmpty()) continue;

            Set<String> theirInterests = Arrays.stream(theirTags.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

            // Đếm số sở thích chung
            long commonCount = theirInterests.stream()
                    .filter(myInterests::contains)
                    .count();

            if (commonCount > 0) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", u.getId());
                item.put("fullName", u.getFullName());
                item.put("avatarUrl", u.getAvatarUrl());
                item.put("commonInterestCount", commonCount);
                appendBlockStatus(item, currentUserId, u.getId());
                suggestions.add(item);
            }
        }

        // Sắp xếp theo số sở thích chung giảm dần
        suggestions.sort((a, b) -> Long.compare(
                (long) b.get("commonInterestCount"),
                (long) a.get("commonInterestCount")));

        // Giới hạn 10 user
        if (suggestions.size() > 10) {
            suggestions = suggestions.subList(0, 10);
        }

        return suggestions;
    }

    public boolean isBlockedBy(Long blockerId, Long currentUserId) {
        if (blockerId == null || currentUserId == null || blockerId.equals(currentUserId)) return false;
        return blockedUserRepository.existsByBlockerIdAndBlockedId(blockerId, currentUserId);
    }

    public void updateFcmToken(Long userId, String fcmToken) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFcmToken(fcmToken);
            userRepository.save(user);
        });
    }

    private int safeAdd(int currentValue, int increment) {
        long value = (long) Math.max(0, currentValue) + Math.max(0, increment);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void createSanctionNotification(User user, Notification.NotificationType type, String message) {
        // UserService da tu gui FCM voi noi dung chuan theo tung che tai,
        // nen chi luu DB + WebSocket de tranh NotificationService ban them push generic "WeConnect".
        try {
            notificationService.createNotificationWithoutPush(user.getId(), type, message, "WeConnect");
        } catch (Exception e) {
            // Notification khong duoc phep lam rollback viec tru diem/khoa tai khoan.
            logger.warning("[NOTIFICATION-SANCTION] Luu notification that bai userId=" + user.getId()
                    + " | type=" + type
                    + " | error=" + e.getMessage());
        }
    }
}
