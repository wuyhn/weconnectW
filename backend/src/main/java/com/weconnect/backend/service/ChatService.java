package com.weconnect.backend.service;

import com.weconnect.backend.dto.ChatMessageResponse;
import com.weconnect.backend.dto.ChatRoomResponse;
import com.weconnect.backend.entity.ChatMessage;
import com.weconnect.backend.entity.ChatRoom;
import com.weconnect.backend.entity.ChatRoomMember;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.BlockedUserRepository;
import com.weconnect.backend.repository.ChatMessageRepository;
import com.weconnect.backend.repository.ChatRoomMemberRepository;
import com.weconnect.backend.repository.ChatRoomRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final PostRepository postRepository;

    public ChatService(ChatRoomRepository chatRoomRepository,
                       ChatRoomMemberRepository chatRoomMemberRepository,
                       ChatMessageRepository chatMessageRepository,
                       UserRepository userRepository,
                       BlockedUserRepository blockedUserRepository,
                       PostRepository postRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.postRepository = postRepository;
    }

    // Danh sách phòng chat của user
    // - Activity room: chỉ ẩn nếu postId null hoặc post bị XÓA
    // - Post hết hạn/archived: room vẫn hiện, chỉ đổi trạng thái hiển thị
    public List<ChatRoomResponse> getUserRooms(Long userId) {
        List<ChatRoomMember> memberships = chatRoomMemberRepository.findByUserId(userId);
        List<ChatRoomResponse> rooms = new ArrayList<>();

        for (ChatRoomMember membership : memberships) {
            ChatRoom room = chatRoomRepository.findById(membership.getRoomId()).orElse(null);
            if (room == null) continue;

            // Activity room: chỉ ẩn nếu postId null hoặc post đã bị XÓA khỏi DB
            if (ChatRoom.TYPE_ACTIVITY.equals(room.getType())) {
                if (room.getPostId() == null || !postRepository.existsById(room.getPostId())) {
                    continue;
                }
            }

            ChatRoomResponse response = toRoomResponse(room);

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
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat."));
        ChatRoomResponse response = toRoomResponse(room);

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
        // Kiểm tra block trước khi tạo/trả về room
        if (blockedUserRepository.existsByBlockerIdAndBlockedId(user1Id, user2Id)
                || blockedUserRepository.existsByBlockerIdAndBlockedId(user2Id, user1Id)) {
            throw new RuntimeException("Không thể nhắn tin với người dùng này.");
        }

        // Tìm phòng direct đã tồn tại
        ChatRoom existing = chatRoomRepository.findDirectRoomBetween(user1Id, user2Id).orElse(null);
        if (existing != null) {
            return toRoomResponse(existing);
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

        return toRoomResponse(room);
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
        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, senderId)) {
            throw new RuntimeException("Bạn không phải thành viên phòng chat này.");
        }

        // Kiểm tra block trong phòng DM
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room != null && ChatRoom.TYPE_DIRECT.equals(room.getType())) {
            var members = chatRoomMemberRepository.findByRoomId(roomId);
            for (var m : members) {
                if (!m.getUserId().equals(senderId)) {
                    if (blockedUserRepository.existsByBlockerIdAndBlockedId(senderId, m.getUserId())
                            || blockedUserRepository.existsByBlockerIdAndBlockedId(m.getUserId(), senderId)) {
                        throw new RuntimeException("Không thể gửi tin nhắn cho người dùng này.");
                    }
                }
            }
        }

        ChatMessage message = ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(content)
                .build();

        message = chatMessageRepository.save(message);
        return toMessageResponse(message, senderId);
    }

    // --- Helpers ---
    private ChatRoomResponse toRoomResponse(ChatRoom room) {
        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(room.getId());
        List<ChatRoomResponse.MemberInfo> memberInfos = new ArrayList<>();
        for (ChatRoomMember m : members) {
            User user = userRepository.findById(m.getUserId()).orElse(null);
            if (user != null) {
                memberInfos.add(ChatRoomResponse.MemberInfo.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .role(m.getRole().name())
                        .avatarUrl(user.getAvatarUrl())
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
                .members(memberInfos)
                .createdAt(room.getCreatedAt())
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg, Long currentUserId) {
        String senderName = "";
        User sender = userRepository.findById(msg.getSenderId()).orElse(null);
        if (sender != null) senderName = sender.getFullName();

        return ChatMessageResponse.builder()
                .id(msg.getId())
                .roomId(msg.getRoomId())
                .senderId(msg.getSenderId())
                .senderName(senderName)
                .content(msg.getContent())
                .sentByCurrentUser(msg.getSenderId().equals(currentUserId))
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private List<ChatMessageResponse> toMessageResponseList(List<ChatMessage> messages, Long currentUserId) {
        List<ChatMessageResponse> responses = new ArrayList<>();
        for (ChatMessage msg : messages) {
            responses.add(toMessageResponse(msg, currentUserId));
        }
        return responses;
    }
}
