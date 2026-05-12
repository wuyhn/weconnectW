package com.weconnect.backend.service;

import com.weconnect.backend.dto.ChangePasswordRequest;
import com.weconnect.backend.dto.UpdateProfileRequest;
import com.weconnect.backend.dto.UserProfileResponse;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

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

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PostRepository postRepository, PostMemberRepository postMemberRepository,
                       FriendshipRepository friendshipRepository, NotificationRepository notificationRepository,
                       ChatRoomMemberRepository chatRoomMemberRepository, UserReviewRepository userReviewRepository,
                       BlockedUserRepository blockedUserRepository, ChatMessageRepository chatMessageRepository) {
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
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        return toProfileResponse(user);
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
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .birthday(user.getBirthday())
                .gender(user.getGender())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .interestTags(user.getInterestTags())
                .averageRating(user.getAverageRating())
                .reputationScore(user.getReputationScore())
                .build();
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

    public void updateFcmToken(Long userId, String fcmToken) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFcmToken(fcmToken);
            userRepository.save(user);
        });
    }
}
