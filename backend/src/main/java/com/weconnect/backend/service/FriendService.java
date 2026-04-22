package com.weconnect.backend.service;

import com.weconnect.backend.entity.BlockedUser;
import com.weconnect.backend.entity.Friendship;
import com.weconnect.backend.entity.Notification;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.BlockedUserRepository;
import com.weconnect.backend.repository.FriendshipRepository;
import com.weconnect.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FriendService(FriendshipRepository friendshipRepository,
                         BlockedUserRepository blockedUserRepository,
                         UserRepository userRepository,
                         NotificationService notificationService) {
        this.friendshipRepository = friendshipRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Helper: lấy friendship giữa 2 user, tự động dọn bản ghi trùng nếu có.
     */
    private Friendship findUniqueBetween(Long user1, Long user2) {
        List<Friendship> list = friendshipRepository.findBetweenUsers(user1, user2);
        if (list == null || list.isEmpty()) return null;
        if (list.size() > 1) {
            // Giữ lại bản ghi đầu tiên, xóa các bản ghi trùng
            for (int i = 1; i < list.size(); i++) {
                friendshipRepository.delete(list.get(i));
            }
        }
        return list.get(0);
    }

    // Gửi lời mời kết bạn
    public String sendFriendRequest(Long senderId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            throw new RuntimeException("Không thể kết bạn với chính mình.");
        }

        // Kiểm tra block 2 chiều
        if (blockedUserRepository.existsByBlockerIdAndBlockedId(senderId, receiverId)
                || blockedUserRepository.existsByBlockerIdAndBlockedId(receiverId, senderId)) {
            throw new RuntimeException("Không thể gửi lời mời.");
        }

        Friendship existing = findUniqueBetween(senderId, receiverId);
        if (existing != null) {
            if (existing.getStatus() == Friendship.Status.ACCEPTED) {
                throw new RuntimeException("Hai bạn đã là bạn bè rồi.");
            }
            if (existing.getStatus() == Friendship.Status.PENDING) {
                throw new RuntimeException("Đã có lời mời kết bạn đang chờ.");
            }
            // DECLINED: xóa record cũ để tạo lại
            if (existing.getStatus() == Friendship.Status.DECLINED) {
                friendshipRepository.delete(existing);
            }
        }

        Friendship friendship = Friendship.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .status(Friendship.Status.PENDING)
                .build();

        friendshipRepository.save(friendship);

        // Tạo thông báo cho người nhận
        User sender = userRepository.findById(senderId).orElse(null);
        String senderName = sender != null ? sender.getFullName() : "Người dùng";
        String message = senderName + " đã gửi cho bạn lời mời kết bạn.";
        notificationService.createNotification(
                receiverId,
                Notification.NotificationType.FRIEND_REQUEST_RECEIVED,
                message,
                senderName,
                null,
                senderId
        );

        return "Đã gửi lời mời kết bạn!";
    }

    // Chấp nhận lời mời
    public String acceptFriendRequest(Long currentUserId, Long fromUserId) {
        Friendship friendship = findUniqueBetween(currentUserId, fromUserId);
        if (friendship == null) {
            throw new RuntimeException("Không tìm thấy lời mời.");
        }

        if (friendship.getStatus() != Friendship.Status.PENDING) {
            throw new RuntimeException("Lời mời không hợp lệ.");
        }

        // Chỉ người nhận mới được chấp nhận
        if (!friendship.getReceiverId().equals(currentUserId)) {
            throw new RuntimeException("Bạn không có quyền chấp nhận lời mời này.");
        }

        friendship.setStatus(Friendship.Status.ACCEPTED);
        friendshipRepository.save(friendship);

        // Tạo thông báo cho người gửi lời mời
        User accepter = userRepository.findById(currentUserId).orElse(null);
        String accepterName = accepter != null ? accepter.getFullName() : "Người dùng";
        String message = accepterName + " đã chấp nhận lời mời kết bạn của bạn.";
        notificationService.createNotification(
                fromUserId,
                Notification.NotificationType.FRIEND_ACCEPTED,
                message,
                accepterName,
                null,
                currentUserId
        );

        return "Đã chấp nhận lời mời kết bạn!";
    }

    // Từ chối lời mời
    public String declineFriendRequest(Long currentUserId, Long fromUserId) {
        Friendship friendship = findUniqueBetween(currentUserId, fromUserId);
        if (friendship == null) {
            throw new RuntimeException("Không tìm thấy lời mời.");
        }

        if (!friendship.getReceiverId().equals(currentUserId)) {
            throw new RuntimeException("Bạn không có quyền từ chối lời mời này.");
        }

        friendship.setStatus(Friendship.Status.DECLINED);
        friendshipRepository.save(friendship);
        return "Đã từ chối lời mời.";
    }

    // Hủy lời mời đã gửi
    public String cancelFriendRequest(Long currentUserId, Long toUserId) {
        Friendship friendship = findUniqueBetween(currentUserId, toUserId);
        if (friendship == null) {
            throw new RuntimeException("Không tìm thấy lời mời.");
        }

        if (!friendship.getSenderId().equals(currentUserId)) {
            throw new RuntimeException("Bạn không có quyền hủy lời mời này.");
        }

        friendshipRepository.delete(friendship);
        return "Đã hủy lời mời kết bạn.";
    }

    // Hủy kết bạn
    public String unfriend(Long currentUserId, Long friendId) {
        Friendship friendship = findUniqueBetween(currentUserId, friendId);
        if (friendship == null) {
            throw new RuntimeException("Không tìm thấy mối quan hệ bạn bè.");
        }

        if (friendship.getStatus() != Friendship.Status.ACCEPTED) {
            throw new RuntimeException("Hai bạn chưa phải là bạn bè.");
        }

        friendshipRepository.delete(friendship);
        return "Đã hủy kết bạn.";
    }

    // Danh sách bạn bè
    public List<Map<String, Object>> getFriendList(Long userId) {
        List<Friendship> friendships = friendshipRepository.findFriendsByUserId(userId);
        List<Map<String, Object>> friends = new ArrayList<>();

        for (Friendship f : friendships) {
            Long friendId = f.getSenderId().equals(userId) ? f.getReceiverId() : f.getSenderId();
            User friend = userRepository.findById(friendId).orElse(null);
            if (friend != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", friend.getId());
                map.put("fullName", friend.getFullName());
                map.put("email", friend.getEmail());
                map.put("avatarUrl", friend.getAvatarUrl());
                friends.add(map);
            }
        }
        return friends;
    }

    // Trạng thái quan hệ
    public String getFriendStatus(Long currentUserId, Long otherUserId) {
        if (currentUserId.equals(otherUserId)) return "SELF";

        if (blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, otherUserId)) {
            return "BLOCKED";
        }

        Friendship friendship = findUniqueBetween(currentUserId, otherUserId);
        if (friendship == null) return "NONE";

        if (friendship.getStatus() == Friendship.Status.ACCEPTED) return "FRIEND";
        if (friendship.getStatus() == Friendship.Status.PENDING) {
            return friendship.getSenderId().equals(currentUserId) ? "PENDING_SENT" : "PENDING_RECEIVED";
        }

        return "NONE";
    }

    // Chặn user
    public String blockUser(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new RuntimeException("Không thể chặn chính mình.");
        }

        if (blockedUserRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new RuntimeException("Đã chặn người này rồi.");
        }

        // Xóa tất cả friendship nếu có (kể cả trùng)
        List<Friendship> existing = friendshipRepository.findBetweenUsers(blockerId, blockedId);
        if (existing != null && !existing.isEmpty()) {
            friendshipRepository.deleteAll(existing);
        }

        BlockedUser blocked = BlockedUser.builder()
                .blockerId(blockerId)
                .blockedId(blockedId)
                .build();

        blockedUserRepository.save(blocked);
        return "Đã chặn người dùng.";
    }

    // Bỏ chặn
    public String unblockUser(Long blockerId, Long blockedId) {
        BlockedUser blocked = blockedUserRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy."));

        blockedUserRepository.delete(blocked);
        return "Đã bỏ chặn.";
    }

    // Danh sách đã chặn
    public List<Map<String, Object>> getBlockedUsers(Long userId) {
        List<BlockedUser> blockedList = blockedUserRepository.findByBlockerId(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (BlockedUser b : blockedList) {
            User user = userRepository.findById(b.getBlockedId()).orElse(null);
            if (user != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", user.getId());
                map.put("fullName", user.getFullName());
                map.put("email", user.getEmail());
                result.add(map);
            }
        }
        return result;
    }

    // Đếm bạn bè
    public int getFriendCount(Long userId) {
        return friendshipRepository.findFriendsByUserId(userId).size();
    }
}

