package com.weconnect.backend.service;

import com.weconnect.backend.dto.ChatMessageResponse;
import com.weconnect.backend.dto.ChatRoomResponse;
import com.weconnect.backend.entity.ChatMessage;
import com.weconnect.backend.entity.ChatRoom;
import com.weconnect.backend.entity.ChatRoomMember;
import com.weconnect.backend.entity.Friendship;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.PostMember;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.BlockedUserRepository;
import com.weconnect.backend.repository.ChatMessageRepository;
import com.weconnect.backend.repository.ChatRoomMemberRepository;
import com.weconnect.backend.repository.ChatRoomRepository;
import com.weconnect.backend.repository.FriendshipRepository;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;
    private final FriendshipRepository friendshipRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(ChatRoomRepository chatRoomRepository,
                       ChatRoomMemberRepository chatRoomMemberRepository,
                       ChatMessageRepository chatMessageRepository,
                       UserRepository userRepository,
                       BlockedUserRepository blockedUserRepository,
                       PostRepository postRepository,
                       PostMemberRepository postMemberRepository,
                       FriendshipRepository friendshipRepository,
                       SimpMessagingTemplate messagingTemplate) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
        this.friendshipRepository = friendshipRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Danh sách phòng chat của user
    // - Activity room: chỉ ẩn nếu postId null hoặc post bị XÓA
    // - Post hết hạn/archived: room vẫn hiện, chỉ đổi trạng thái hiển thị
    public List<ChatRoomResponse> getUserRooms(Long userId) {
        updateLastActiveAt(userId);
        List<ChatRoomMember> memberships = chatRoomMemberRepository.findByUserId(userId);
        List<ChatRoomResponse> rooms = new ArrayList<>();

        for (ChatRoomMember membership : memberships) {
            ChatRoom room = chatRoomRepository.findById(membership.getRoomId()).orElse(null);
            if (room == null) continue;

            // Activity room: ẩn nếu postId null, post đã bị xóa, hoặc hoạt động đã bị hủy
            if (ChatRoom.TYPE_ACTIVITY.equals(room.getType())) {
                if (room.getPostId() == null || !postRepository.existsById(room.getPostId())) {
                    continue;
                }
                if (!room.isActive() && "CANCELLED".equals(room.getInactiveStatusLabel())) {
                    continue;
                }
            }

            ChatRoomResponse response = toRoomResponse(room, userId);

            // Direct room: hiển thị tên người còn lại, không phải tên mình
            if (ChatRoom.TYPE_DIRECT.equals(room.getType()) && response.getMembers() != null) {
                for (ChatRoomResponse.MemberInfo member : response.getMembers()) {
                    if (!userId.equals(member.getId())) {
                        response.setTitle(member.getFullName());
                        break;
                    }
                }
            }

            rooms.add(response);
        }
        return rooms;
    }

    // Direct rooms from users who are not accepted friends with current user.
    public List<ChatRoomResponse> getMessageRequests(Long userId) {
        List<ChatRoomMember> memberships = chatRoomMemberRepository.findByUserId(userId);
        List<ChatRoomResponse> rooms = new ArrayList<>();

        for (ChatRoomMember membership : memberships) {
            ChatRoom room = chatRoomRepository.findById(membership.getRoomId()).orElse(null);
            if (room == null || !ChatRoom.TYPE_DIRECT.equals(room.getType())) {
                continue;
            }

            ChatRoomResponse response = toRoomResponse(room, userId);
            if (Boolean.TRUE.equals(response.getIsMessageRequest())) {
                rooms.add(response);
            }
        }

        rooms.sort((a, b) -> safeString(b.getLastMessageTime()).compareTo(safeString(a.getLastMessageTime())));
        return rooms;
    }

    // Xóa tất cả room activity không hợp lệ (postId null hoặc post đã bị xóa)
    public int cleanupInvalidRooms() {
        List<ChatRoom> allRooms = chatRoomRepository.findAll();
        int deleted = 0;
        for (ChatRoom room : allRooms) {
            if (ChatRoom.TYPE_ACTIVITY.equals(room.getType())) {
                if (room.getPostId() == null || !postRepository.existsById(room.getPostId())) {
                    // Xóa members và messages trước
                    List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(room.getId());
                    chatRoomMemberRepository.deleteAll(members);
                    List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId());
                    chatMessageRepository.deleteAll(messages);
                    chatRoomRepository.delete(room);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    // Xóa thành viên khỏi nhóm (chỉ owner)
    public void removeMember(Long roomId, Long requesterId, Long targetUserId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat."));
        if (!room.getOwnerId().equals(requesterId)) {
            throw new RuntimeException("Chỉ chủ phòng mới có quyền xóa thành viên.");
        }
        if (requesterId.equals(targetUserId)) {
            throw new RuntimeException("Chủ phòng không thể tự xóa chính mình.");
        }
        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, targetUserId)) {
            throw new RuntimeException("Người dùng không phải thành viên của phòng này.");
        }
        String targetUserName = userRepository.findById(targetUserId)
                .map(User::getFullName).orElse("Người dùng");
        chatRoomMemberRepository.deleteByRoomIdAndUserId(roomId, targetUserId);

        // Nếu là phòng hoạt động, cập nhật PostMember thành REJECTED để:
        // 1. Giảm memberCount trên bài post ngay lập tức
        // 2. Ngăn user bị kick tự join lại từ bài post
        if (ChatRoom.TYPE_ACTIVITY.equals(room.getType()) && room.getPostId() != null) {
            postMemberRepository.findByPostIdAndUserId(room.getPostId(), targetUserId)
                    .ifPresent(pm -> {
                        pm.setStatus(PostMember.Status.REJECTED);
                        postMemberRepository.save(pm);
                    });
        }

        // System message cho các thành viên còn lại
        sendSystemMessage(roomId, targetUserName + " đã rời khỏi nhóm.");

        // Gửi WebSocket KICKED event đến user bị kick để frontend xử lý realtime
        messagingTemplate.convertAndSendToUser(
                targetUserId.toString(),
                "/queue/room-events",
                Map.of("type", "KICKED", "roomId", roomId)
        );

        // Refresh chat list cho user bị kick (để xóa room khỏi danh sách)
        messagingTemplate.convertAndSendToUser(
                targetUserId.toString(),
                "/queue/chat-list",
                Map.of("roomId", roomId)
        );

        // Refresh chat list cho các thành viên còn lại (để cập nhật memberCount)
        List<ChatRoomMember> remaining = chatRoomMemberRepository.findByRoomId(roomId);
        for (ChatRoomMember member : remaining) {
            messagingTemplate.convertAndSendToUser(
                    member.getUserId().toString(),
                    "/queue/chat-list",
                    Map.of("roomId", roomId)
            );
        }
    }

    // Rời nhóm (chỉ dành cho non-owner)
    public void leaveRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat."));
        if (room.getOwnerId().equals(userId)) {
            throw new RuntimeException("Chủ phòng không thể rời nhóm. Hãy xóa nhóm thay thế.");
        }
        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new RuntimeException("Bạn không phải thành viên của phòng này.");
        }
        String userName = userRepository.findById(userId)
                .map(User::getFullName).orElse("Người dùng");
        chatRoomMemberRepository.deleteByRoomIdAndUserId(roomId, userId);

        // Nếu là phòng hoạt động: xóa PostMember để memberCount trên bài post giảm
        // (xóa thay vì REJECTED → user vẫn có thể xin join lại)
        if (ChatRoom.TYPE_ACTIVITY.equals(room.getType()) && room.getPostId() != null) {
            postMemberRepository.deleteByPostIdAndUserId(room.getPostId(), userId);
        }

        sendSystemMessage(roomId, userName + " đã rời khỏi nhóm.");

        // Refresh chat list cho các thành viên còn lại (để cập nhật memberCount)
        List<ChatRoomMember> remaining = chatRoomMemberRepository.findByRoomId(roomId);
        for (ChatRoomMember member : remaining) {
            messagingTemplate.convertAndSendToUser(
                    member.getUserId().toString(),
                    "/queue/chat-list",
                    Map.of("roomId", roomId)
            );
        }
    }

    // Trả về postId từ room (để ChatController có thể delegate sang PostService)
    public Long getPostIdFromActivityRoom(Long roomId, Long requesterId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat."));
        if (!ChatRoom.TYPE_ACTIVITY.equals(room.getType())) {
            throw new RuntimeException("Đây không phải phòng chat hoạt động.");
        }
        if (!room.getOwnerId().equals(requesterId)) {
            throw new RuntimeException("Chỉ chủ phòng mới có quyền hủy hoạt động.");
        }
        if (room.getPostId() == null) {
            throw new RuntimeException("Phòng chat không liên kết với hoạt động nào.");
        }
        return room.getPostId();
    }

    // Hủy phòng chat hoạt động (được gọi khi chủ post hủy hoạt động)
    public void cancelActivityRoom(Long postId) {
        ChatRoom room = chatRoomRepository.findByPostId(postId).orElse(null);
        if (room == null) return;

        room.setActive(false);
        room.setInactiveStatusLabel("CANCELLED");
        chatRoomRepository.save(room);

        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(room.getId());
        for (ChatRoomMember member : members) {
            messagingTemplate.convertAndSendToUser(
                    member.getUserId().toString(),
                    "/queue/room-events",
                    Map.of("type", "ACTIVITY_CANCELLED", "roomId", room.getId())
            );
            messagingTemplate.convertAndSendToUser(
                    member.getUserId().toString(),
                    "/queue/chat-list",
                    Map.of("roomId", room.getId())
            );
        }
    }

    // Xóa phòng chat (chỉ owner mới được xóa)
    public void deleteRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat."));

        if (!room.getOwnerId().equals(userId)) {
            throw new RuntimeException("Chỉ chủ phòng mới có quyền xóa phòng chat.");
        }

        // Xóa members và messages trước
        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(roomId);
        chatRoomMemberRepository.deleteAll(members);
        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        chatMessageRepository.deleteAll(messages);
        chatRoomRepository.delete(room);
    }

    // Chi tiết phòng chat
    public ChatRoomResponse getRoom(Long roomId, Long userId) {
        if (userId != null) updateLastActiveAt(userId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat."));
        // Hoạt động đã bị hủy bởi chủ phòng
        if (ChatRoom.TYPE_ACTIVITY.equals(room.getType())
                && !room.isActive() && "CANCELLED".equals(room.getInactiveStatusLabel())) {
            throw new RuntimeException("Hoạt động không khả dụng.");
        }
        // Kiểm tra user có còn là thành viên không (guard cho trường hợp bị kick, truy cập qua cache/deep link)
        if (userId != null && !chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new RuntimeException("Bạn không còn là thành viên của nhóm này.");
        }
        ChatRoomResponse response = toRoomResponse(room, userId);

        // Direct room: hiển thị tên người còn lại, không phải tên mình
        if (ChatRoom.TYPE_DIRECT.equals(room.getType()) && response.getMembers() != null && userId != null) {
            for (ChatRoomResponse.MemberInfo member : response.getMembers()) {
                if (!userId.equals(member.getId())) {
                    response.setTitle(member.getFullName());
                    break;
                }
            }
        }

        return response;
    }

    // Tìm phòng chat theo postId
    public ChatRoomResponse getRoomByPostId(Long postId) {
        ChatRoom room = chatRoomRepository.findByPostId(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat cho bài viết này."));
        return toRoomResponse(room);
    }

    // Tạo phòng nhóm bạn bè
    public ChatRoomResponse createGroupRoom(Long ownerId, String title, List<Long> memberIds) {
        ChatRoom room = ChatRoom.builder()
                .title(title)
                .type(ChatRoom.TYPE_FRIEND_GROUP)
                .ownerId(ownerId)
                .active(true)
                .build();
        room = chatRoomRepository.save(room);

        // Thêm owner làm member
        chatRoomMemberRepository.save(ChatRoomMember.builder()
                .roomId(room.getId())
                .userId(ownerId)
                .role(ChatRoomMember.Role.OWNER)
                .build());

        // Thêm các thành viên
        for (Long memberId : memberIds) {
            if (!memberId.equals(ownerId)) {
                chatRoomMemberRepository.save(ChatRoomMember.builder()
                        .roomId(room.getId())
                        .userId(memberId)
                        .role(ChatRoomMember.Role.MEMBER)
                        .build());
            }
        }

        return toRoomResponse(room);
    }
    // Tạo phòng nhóm hoạt động (linked to post)
    // Rule 1: Chỉ tạo khi post hợp lệ, 1 post = tối đa 1 room
    public ChatRoomResponse createActivityChatRoom(Long postId, Long ownerId, String title) {
        // Validate postId phải hợp lệ
        if (postId == null) {
            throw new RuntimeException("Không thể tạo phòng chat: postId không hợp lệ.");
        }
        if (!postRepository.existsById(postId)) {
            throw new RuntimeException("Không thể tạo phòng chat: bài viết không tồn tại.");
        }

        // Kiểm tra đã có phòng cho post này chưa (Rule: 1 post = 1 room)
        var existing = chatRoomRepository.findByPostId(postId);
        if (existing.isPresent()) {
            return toRoomResponse(existing.get());
        }

        ChatRoom room = ChatRoom.builder()
                .title(title)
                .type(ChatRoom.TYPE_ACTIVITY)
                .ownerId(ownerId)
                .postId(postId)
                .active(true)
                .build();
        room = chatRoomRepository.save(room);

        // Thêm owner làm member
        chatRoomMemberRepository.save(ChatRoomMember.builder()
                .roomId(room.getId())
                .userId(ownerId)
                .role(ChatRoomMember.Role.OWNER)
                .build());

        return toRoomResponse(room);
    }

    // Thêm user vào phòng chat hoạt động
    public void addMemberToActivityRoom(Long postId, Long userId) {
        var roomOpt = chatRoomRepository.findByPostId(postId);
        if (roomOpt.isEmpty()) return;

        ChatRoom room = roomOpt.get();
        // Kiểm tra xem user đã là member chưa
        var existingMembers = chatRoomMemberRepository.findByRoomId(room.getId());
        boolean alreadyMember = existingMembers.stream()
                .anyMatch(m -> m.getUserId().equals(userId));
        if (alreadyMember) return;

        chatRoomMemberRepository.save(ChatRoomMember.builder()
                .roomId(room.getId())
                .userId(userId)
                .role(ChatRoomMember.Role.MEMBER)
                .build());
    }

    // Lấy hoặc tạo phòng DM
    public ChatRoomResponse getOrCreateDirectRoom(Long user1Id, Long user2Id) {
        if (user1Id.equals(user2Id)) {
            throw new RuntimeException("Không thể nhắn tin cho chính mình.");
        }

        // Tìm phòng direct đã tồn tại trước khi kiểm tra block
        ChatRoom existing = chatRoomRepository.findDirectRoomBetween(user1Id, user2Id).orElse(null);

        // Block relation: cho phép mở lại room cũ, không cho tạo room mới
        if (isBlockedBetweenUsers(user1Id, user2Id)) {
            if (existing != null) {
                return toRoomResponse(existing, user1Id);
            }
            throw new RuntimeException("Bạn không thể nhắn tin cho người này.");
        }

        if (existing != null) {
            return toRoomResponse(existing, user1Id);
        }

        // Tạo phòng mới
        User otherUser = userRepository.findById(user2Id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

        ChatRoom room = ChatRoom.builder()
                .title(otherUser.getFullName())
                .type(ChatRoom.TYPE_DIRECT)
                .ownerId(user1Id)
                .active(true)
                .build();
        room = chatRoomRepository.save(room);

        chatRoomMemberRepository.save(ChatRoomMember.builder()
                .roomId(room.getId()).userId(user1Id).role(ChatRoomMember.Role.OWNER).build());
        chatRoomMemberRepository.save(ChatRoomMember.builder()
                .roomId(room.getId()).userId(user2Id).role(ChatRoomMember.Role.MEMBER).build());

        return toRoomResponse(room, user1Id);
    }

    // Lịch sử tin nhắn
    public List<ChatMessageResponse> getMessages(Long roomId, Long currentUserId) {
        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        return toMessageResponseList(messages, currentUserId);
    }

    // Tin nhắn mới (polling)
    public List<ChatMessageResponse> getNewMessages(Long roomId, Long afterId, Long currentUserId) {
        List<ChatMessage> messages = chatMessageRepository
                .findByRoomIdAndIdGreaterThanOrderByCreatedAtAsc(roomId, afterId);
        return toMessageResponseList(messages, currentUserId);
    }

    // Gửi tin nhắn
    public ChatMessageResponse sendMessage(Long roomId, Long senderId, String content) {
        updateLastActiveAt(senderId);
        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, senderId)) {
            throw new RuntimeException("Bạn không phải thành viên phòng chat này.");
        }

        // Kiểm tra block trong phòng DM
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room != null && ChatRoom.TYPE_ACTIVITY.equals(room.getType())
                && !room.isActive() && "CANCELLED".equals(room.getInactiveStatusLabel())) {
            throw new RuntimeException("Hoạt động này đã bị hủy.");
        }
        if (room != null && ChatRoom.TYPE_DIRECT.equals(room.getType())) {
            var members = chatRoomMemberRepository.findByRoomId(roomId);
            for (var m : members) {
                if (!m.getUserId().equals(senderId)) {
                    if (isBlockedBetweenUsers(senderId, m.getUserId())) {
                        throw new RuntimeException("Bạn không thể nhắn tin cho người này.");
                    }
                }
            }
        }

        ChatMessage message = ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(content)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);
        // Người gửi tự động đánh dấu đã đọc tin vừa gửi
        chatRoomMemberRepository.findByRoomIdAndUserId(roomId, senderId).ifPresent(member -> {
            member.setLastReadMessageId(savedMessage.getId());
            chatRoomMemberRepository.save(member);
        });
        return toMessageResponse(savedMessage, senderId);
    }

    // --- Helpers ---
    private boolean isBlockedBetweenUsers(Long user1Id, Long user2Id) {
        return blockedUserRepository.existsByBlockerIdAndBlockedId(user1Id, user2Id)
                || blockedUserRepository.existsByBlockerIdAndBlockedId(user2Id, user1Id);
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room) {
        return toRoomResponse(room, null);
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room, Long currentUserId) {
        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(room.getId());
        List<ChatRoomResponse.MemberInfo> memberInfos = new ArrayList<>();
        for (ChatRoomMember m : members) {
            User user = userRepository.findById(m.getUserId()).orElse(null);
            if (user != null) {
                boolean memberBlockedByMe = false;
                boolean memberHasBlockedMe = false;
                if (currentUserId != null && user.getId() != null && !currentUserId.equals(user.getId())) {
                    memberBlockedByMe = blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, user.getId());
                    memberHasBlockedMe = blockedUserRepository.existsByBlockerIdAndBlockedId(user.getId(), currentUserId);
                }
                memberInfos.add(ChatRoomResponse.MemberInfo.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .role(m.getRole().name())
                        .avatarUrl(user.getAvatarUrl())
                        .isBlockedByMe(memberBlockedByMe)
                        .hasBlockedMe(memberHasBlockedMe)
                        .isBlockedBetweenUsers(memberBlockedByMe || memberHasBlockedMe)
                        .build());
            }
        }

        String ownerName = "";
        if (room.getOwnerId() != null) {
            User owner = userRepository.findById(room.getOwnerId()).orElse(null);
            if (owner != null) ownerName = owner.getFullName();
        }

        // === Activity room: title, subtitle, postStatusLabel từ post ===
        String roomTitle = room.getTitle();
        String subtitle = null;
        String postStatusLabel = null;
        Boolean isFriend = null;
        Boolean isMessageRequest = null;
        Long otherUserId = null;
        String otherUserName = null;
        String otherUserAvatarUrl = null;
        Boolean isBlockedByMe = null;
        Boolean hasBlockedMe = null;
        Boolean isBlockedBetweenUsers = null;
        String activityDateDisplay = null;
        int maxMembers = 0;

        if (ChatRoom.TYPE_ACTIVITY.equals(room.getType()) && room.getPostId() != null) {
            Post post = postRepository.findById(room.getPostId()).orElse(null);
            if (post != null) {
                // Title: interestTag + " - " + ownerName (Rule 5)
                String tag = (post.getInterestTag() != null && !post.getInterestTag().isEmpty())
                        ? post.getInterestTag() : "Hoạt động";
                roomTitle = tag + " - " + ownerName;

                // Đồng bộ title vào room nếu khác
                if (!roomTitle.equals(room.getTitle())) {
                    room.setTitle(roomTitle);
                    chatRoomRepository.save(room);
                }

                // Subtitle: time · location (Rule 5 fallback context)
                StringBuilder sb = new StringBuilder();
                if (post.getStartTime() != null) {
                    sb.append(post.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")));
                }
                if (post.getLocation() != null && !post.getLocation().isEmpty()) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append(post.getLocation());
                }
                if (sb.length() > 0) {
                    subtitle = sb.toString();
                }

                // Post status label (hiển thị, KHÔNG khóa chat)
                if (post.isExpired()) {
                    postStatusLabel = "Hoạt động đã kết thúc";
                } else if (post.isArchived()) {
                    postStatusLabel = "Đã lưu trữ";
                }

                // Ngày diễn ra hoạt động (dd/MM/yyyy hoặc khoảng ngày)
                if (post.getStartTime() != null) {
                    java.time.LocalDate startDate = post.getStartTime().toLocalDate();
                    String startStr = startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    java.time.LocalDateTime endDt = post.getActivityEndTime() != null
                            ? post.getActivityEndTime() : post.getEndTime();
                    if (endDt != null) {
                        java.time.LocalDate endDate = endDt.toLocalDate();
                        if (!startDate.equals(endDate)) {
                            activityDateDisplay = startStr + " - "
                                    + endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        } else {
                            activityDateDisplay = startStr;
                        }
                    } else {
                        activityDateDisplay = startStr;
                    }
                }
                maxMembers = post.getMaxMembers();
            }
        }

        if (ChatRoom.TYPE_DIRECT.equals(room.getType()) && currentUserId != null) {
            for (ChatRoomResponse.MemberInfo member : memberInfos) {
                if (member.getId() != null && !currentUserId.equals(member.getId())) {
                    otherUserId = member.getId();
                    otherUserName = member.getFullName();
                    otherUserAvatarUrl = member.getAvatarUrl();
                    break;
                }
            }

            if (otherUserId != null) {
                roomTitle = otherUserName != null && !otherUserName.isBlank()
                        ? otherUserName : roomTitle;
                isFriend = isAcceptedFriendship(currentUserId, otherUserId);
                isMessageRequest = !isFriend;
                isBlockedByMe = blockedUserRepository.existsByBlockerIdAndBlockedId(currentUserId, otherUserId);
                hasBlockedMe = blockedUserRepository.existsByBlockerIdAndBlockedId(otherUserId, currentUserId);
                isBlockedBetweenUsers = isBlockedByMe || hasBlockedMe;
            }
        }

        // Trạng thái hoạt động của người đối diện trong direct room
        Boolean otherUserOnline = null;
        Long otherUserLastActiveMins = null;
        if (ChatRoom.TYPE_DIRECT.equals(room.getType()) && currentUserId != null && otherUserId != null
                && Boolean.TRUE.equals(isFriend)
                && !Boolean.TRUE.equals(isBlockedByMe)
                && !Boolean.TRUE.equals(hasBlockedMe)) {
            User otherUser = userRepository.findById(otherUserId).orElse(null);
            if (otherUser != null && !Boolean.FALSE.equals(otherUser.getActivityStatusEnabled())
                    && otherUser.getLastActiveAt() != null) {
                long minutesAgo = java.time.Duration.between(
                        otherUser.getLastActiveAt(), java.time.LocalDateTime.now()).toMinutes();
                if (minutesAgo < 0) minutesAgo = 0;
                if (minutesAgo < 5) {
                    otherUserOnline = true;
                    otherUserLastActiveMins = minutesAgo;
                } else if (minutesAgo < 1440) {
                    otherUserOnline = false;
                    otherUserLastActiveMins = minutesAgo;
                }
                // >= 1440 phút (24h): không trả về trạng thái
            }
        }

        // Số tin nhắn chưa đọc của currentUser trong phòng này
        int unreadCount = 0;
        if (currentUserId != null) {
            ChatRoomMember myMembership = chatRoomMemberRepository
                    .findByRoomIdAndUserId(room.getId(), currentUserId).orElse(null);
            if (myMembership != null) {
                if (myMembership.getLastReadMessageId() != null) {
                    unreadCount = chatMessageRepository.countByRoomIdAndIdGreaterThan(
                            room.getId(), myMembership.getLastReadMessageId());
                } else {
                    // Chưa đọc lần nào → tính tất cả tin nhắn trong phòng là chưa đọc
                    unreadCount = chatMessageRepository.countByRoomIdAndIdGreaterThan(room.getId(), 0L);
                }
            }
        }

        // Last message
        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId());
        String lastPreview = "Chưa có tin nhắn";
        String lastTime = "";
        if (!messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            lastPreview = last.getContent();
            lastTime = last.getCreatedAt() != null ? last.getCreatedAt().toString() : "";
        }

        // isMessageRequest = true chỉ khi người lạ nhắn trước và current user chưa phản hồi.
        // Nếu current user đã từng gửi tin trong room này → không còn là message request.
        if (Boolean.TRUE.equals(isMessageRequest) && currentUserId != null) {
            boolean hasReplied = messages.stream()
                    .anyMatch(m -> currentUserId.equals(m.getSenderId()));
            if (hasReplied) isMessageRequest = false;
        }

        return ChatRoomResponse.builder()
                .id(room.getId())
                .postId(room.getPostId())
                .title(roomTitle)
                .type(room.getType())
                .ownerId(room.getOwnerId())
                .ownerName(ownerName)
                .active(room.isActive())
                .inactiveStatusLabel(room.getInactiveStatusLabel())
                .subtitle(subtitle)
                .postStatusLabel(postStatusLabel)
                .lastMessagePreview(lastPreview)
                .lastMessageTime(lastTime)
                .isFriend(isFriend)
                .isMessageRequest(isMessageRequest)
                .otherUserId(otherUserId)
                .otherUserName(otherUserName)
                .otherUserAvatarUrl(otherUserAvatarUrl)
                .isBlockedByMe(isBlockedByMe)
                .hasBlockedMe(hasBlockedMe)
                .isBlockedBetweenUsers(isBlockedBetweenUsers)
                .otherUserOnline(otherUserOnline)
                .otherUserLastActiveMins(otherUserLastActiveMins)
                .unreadCount(unreadCount)
                .activityDateDisplay(activityDateDisplay)
                .memberCount(memberInfos.size())
                .maxMembers(maxMembers)
                .members(memberInfos)
                .createdAt(room.getCreatedAt() != null ? room.getCreatedAt().toString() : "")
                .build();
    }

    private boolean isAcceptedFriendship(Long user1Id, Long user2Id) {
        return friendshipRepository.findBetweenUsers(user1Id, user2Id).stream()
                .anyMatch(f -> Friendship.Status.ACCEPTED.equals(f.getStatus()));
    }

    private void updateLastActiveAt(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastActiveAt(java.time.LocalDateTime.now());
            userRepository.save(user);
        });
    }

    public void markAsRead(Long roomId, Long userId) {
        chatMessageRepository.findTopByRoomIdOrderByIdDesc(roomId).ifPresent(latest -> {
            chatRoomMemberRepository.findByRoomIdAndUserId(roomId, userId).ifPresent(member -> {
                member.setLastReadMessageId(latest.getId());
                chatRoomMemberRepository.save(member);
            });
        });
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg, Long currentUserId) {
        boolean isSystem = "SYSTEM".equals(msg.getType());
        String senderName = "";
        if (!isSystem && msg.getSenderId() != null && msg.getSenderId() != 0) {
            User sender = userRepository.findById(msg.getSenderId()).orElse(null);
            if (sender != null) senderName = sender.getFullName();
        }

        return ChatMessageResponse.builder()
                .id(msg.getId())
                .roomId(msg.getRoomId())
                .senderId(isSystem ? 0L : msg.getSenderId())
                .senderName(senderName)
                .content(msg.getContent())
                .type(msg.getType())
                .sentByCurrentUser(!isSystem && msg.getSenderId() != null && msg.getSenderId().equals(currentUserId))
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private void sendSystemMessage(Long roomId, String content) {
        ChatMessage msg = ChatMessage.builder()
                .roomId(roomId)
                .senderId(0L)
                .content(content)
                .type("SYSTEM")
                .build();
        ChatMessage saved = chatMessageRepository.save(msg);
        ChatMessageResponse response = toMessageResponse(saved, -1L);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);
    }

    private List<ChatMessageResponse> toMessageResponseList(List<ChatMessage> messages, Long currentUserId) {
        List<ChatMessageResponse> responses = new ArrayList<>();
        for (ChatMessage msg : messages) {
            responses.add(toMessageResponse(msg, currentUserId));
        }
        return responses;
    }
}
