package com.weconnect.backend.service;

import com.weconnect.backend.dto.ChangePasswordRequest;
import com.weconnect.backend.dto.UpdateProfileRequest;
import com.weconnect.backend.dto.UserProfileResponse;
import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.SystemViolationLog;
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
    private final SystemViolationLogRepository systemViolationLogRepository;
    private final ReviewService reviewService;
    private final NotificationService notificationService;
    private final FCMService fcmService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PostRepository postRepository, PostMemberRepository postMemberRepository,
                       FriendshipRepository friendshipRepository, NotificationRepository notificationRepository,
                       ChatRoomMemberRepository chatRoomMemberRepository, UserReviewRepository userReviewRepository,
                       BlockedUserRepository blockedUserRepository, ChatMessageRepository chatMessageRepository,
                       SystemViolationLogRepository systemViolationLogRepository,
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
        this.systemViolationLogRepository = systemViolationLogRepository;
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
        if (request.getProvinceId() != null) user.setProvinceId(request.getProvinceId());
        if (request.getProvinceName() != null) user.setProvinceName(request.getProvinceName());
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

    /**
     * Xử lý vi phạm tự động (AI, wrong-tag, WebSocket...) — những vi phạm KHÔNG được
     * ghi nhận trong bảng reports.
     *
     * Luồng mới:
     *   1. Tạo bản ghi SystemViolationLog (penaltyPoint dương = phạt)
     *   2. Gọi recalculateReputation() để tính lại điểm từ toàn bộ lịch sử DB
     *   3. ReputationSanctionService.applySanctionBasedOnScore() tự xử lý lock/ban nếu score về 0
     *   4. Nếu score vẫn > 0, gửi notification cảnh báo tại đây
     */
    @Transactional
    public void handleUserViolation(Long userId, int penaltyPoints) {
        if (penaltyPoints <= 0) {
            throw new IllegalArgumentException("Số điểm phạt phải lớn hơn 0.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        logger.info("[AUTO-VIOLATION] userId=" + userId + " | penaltyPoints=" + penaltyPoints
                + " | scoreTruoc=" + user.getReputationScore());

        // Bước 1: Lưu vết phạt vào SystemViolationLog — nguồn dữ liệu lịch sử duy nhất
        SystemViolationLog log = SystemViolationLog.builder()
                .userId(userId)
                .violationType("AUTO_VIOLATION")
                .penaltyPoint(penaltyPoints)
                .build();
        systemViolationLogRepository.save(log);

        // Bước 2 + 3: Tái tính toàn bộ điểm từ DB; áp chế tài nếu score về 0 (idempotency trong service)
        reviewService.recalculateReputation(userId);

        // Bước 4: Gửi notification cảnh báo nếu score vẫn > 0 (lock/ban notification đã được gửi bởi ReputationSanctionService)
        double newScore = userRepository.findById(userId)
                .map(User::getReputationScore).orElse(0.0);
        if (newScore > 0) {
            String msg = "Tài khoản của bạn vừa bị trừ " + penaltyPoints
                    + " điểm uy tín do vi phạm quy chuẩn cộng đồng. Điểm còn lại: "
                    + Math.round(newScore) + ".";
            try {
                notificationService.createNotificationWithoutPush(
                        userId, Notification.NotificationType.ADMIN_WARNING, msg, "WeConnect");
            } catch (Exception e) {
                logger.warning("[AUTO-VIOLATION] Lưu notification thất bại userId=" + userId
                        + ": " + e.getMessage());
            }
            String fcmToken = user.getFcmToken();
            if (fcmToken != null && !fcmToken.isBlank()) {
                try {
                    fcmService.sendNotification(fcmToken,
                            "⚠️ Cảnh báo vi phạm quy chuẩn uy tín",
                            "Tài khoản của bạn vừa bị trừ " + penaltyPoints
                                    + " điểm uy tín. Số điểm còn lại: " + Math.round(newScore)
                                    + " điểm. Vui lòng tuân thủ tiêu chuẩn cộng đồng.",
                            null);
                } catch (Exception e) {
                    logger.warning("[AUTO-VIOLATION] Gửi FCM thất bại userId=" + userId
                            + ": " + e.getMessage());
                }
            }
        }
        // Nếu score <= 0: ReputationSanctionService đã gửi notification lock/ban
    }

    @Transactional
    public void restoreExpiredTemporaryLock(User user) {
        // Mở khóa và cấp lại 30 điểm uy tín nền sau khi hết thời hạn khóa tạm.
        // Không gọi recalculateReputation() ở đây vì penalty cũ có thể kéo score về 0 ngay lập tức.
        // Thay vào đó: tạo record tha phạt âm (-30) trong SystemViolationLog để lịch sử nhất quán.
        user.setStatus(User.STATUS_ACTIVE);
        user.setBlocked(false);
        user.setLockUntil(null);
        user.setReputationScore(30);
        userRepository.save(user);

        // Record tha phạt: recalculate() trong tương lai sẽ tính được khoản tha này từ lịch sử
        SystemViolationLog forgiveness = SystemViolationLog.builder()
                .userId(user.getId())
                .violationType("LOCK_EXPIRY_FORGIVENESS")
                .penaltyPoint(-30)
                .build();
        systemViolationLogRepository.save(forgiveness);
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
                .provinceId(blockedBetweenUsers ? null : user.getProvinceId())
                .provinceName(blockedBetweenUsers ? null : user.getProvinceName())
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

    // Xóa liên kết FCM token khi người dùng đăng xuất.
    // Chỉ xóa nếu token trùng khớp để tránh vô tình xóa token thiết bị khác
    // trong trường hợp đa thiết bị (future-proof).
    @Transactional
    public void removeFcmToken(Long userId, String fcmToken) {
        if (userId == null || fcmToken == null || fcmToken.isBlank()) return;
        userRepository.findById(userId).ifPresent(user -> {
            if (fcmToken.equals(user.getFcmToken())) {
                user.setFcmToken(null);
                userRepository.save(user);
            }
        });
    }

}
