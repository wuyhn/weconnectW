package com.weconnect.backend.controller;

import com.weconnect.backend.dto.ChatMessageResponse;
import com.weconnect.backend.entity.ChatRoomMember;
import com.weconnect.backend.repository.ChatMessageRepository;
import com.weconnect.backend.repository.ChatRoomMemberRepository;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.service.ChatService;
import com.weconnect.backend.service.FCMService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;

    public ChatWebSocketController(ChatService chatService,
                                   SimpMessagingTemplate messagingTemplate,
                                   ChatRoomMemberRepository chatRoomMemberRepository,
                                   ChatMessageRepository chatMessageRepository,
                                   UserRepository userRepository,
                                   FCMService fcmService) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.fcmService = fcmService;
    }

    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId,
                            @Payload Map<String, String> payload,
                            Principal principal) {
        if (principal == null) return;

        Long senderId = Long.parseLong(principal.getName());
        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) return;

        // Lưu vào DB. ChatService.sendMessage() ném RuntimeException nếu vi phạm rule.
        ChatMessageResponse saved;
        try {
            saved = chatService.sendMessage(roomId, senderId, content);
        } catch (RuntimeException e) {
            messagingTemplate.convertAndSendToUser(
                    senderId.toString(),
                    "/queue/errors",
                    Map.of("code", 403, "message", e.getMessage(), "roomId", roomId)
            );
            return;
        }

        // Broadcast tin nhắn tới tất cả subscriber của phòng
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, saved);

        // Chuẩn bị preview và time để đính kèm vào payload chat-list
        String preview = saved.getContent() != null
                ? (saved.getContent().length() > 40
                        ? saved.getContent().substring(0, 40) + "…"
                        : saved.getContent())
                : "";
        String time = saved.getCreatedAt() != null
                ? saved.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm"))
                : "";

        // Gửi badge update tới từng thành viên trong phòng
        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(roomId);
        for (ChatRoomMember member : members) {
            Long lastRead = member.getLastReadMessageId() != null ? member.getLastReadMessageId() : 0L;
            // countByRoomIdAndIdGreaterThan trả về 0 cho sender vì họ auto-read trong sendMessage()
            int unreadCount = chatMessageRepository.countByRoomIdAndIdGreaterThan(roomId, lastRead);

            // WebSocket: cập nhật badge real-time nếu user đang online
            messagingTemplate.convertAndSendToUser(
                    member.getUserId().toString(),
                    "/queue/chat-list",
                    Map.of(
                            "roomId", roomId,
                            "unreadCount", unreadCount,
                            "lastMessagePreview", preview,
                            "lastMessageTime", time
                    )
            );

            // FCM: gửi data-only message tới người nhận để hiển thị thông báo khi offline
            if (!member.getUserId().equals(senderId)) {
                userRepository.findById(member.getUserId()).ifPresent(memberUser -> {
                    String token = memberUser.getFcmToken();
                    if (token != null && !token.isBlank()) {
                        Map<String, String> fcmData = new HashMap<>();
                        fcmData.put("type", "NEW_CHAT_MESSAGE");
                        fcmData.put("roomId", String.valueOf(roomId));
                        fcmData.put("badgeCount", String.valueOf(unreadCount));
                        fcmData.put("lastMessagePreview", preview);
                        fcmData.put("lastMessageTime", time);
                        fcmService.sendDataOnlyMessage(token, fcmData);
                    }
                });
            }
        }
    }
}
