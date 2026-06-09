package com.weconnect.backend.controller;

import com.weconnect.backend.dto.ChatMessageResponse;
import com.weconnect.backend.entity.ChatRoomMember;
import com.weconnect.backend.repository.ChatRoomMemberRepository;
import com.weconnect.backend.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public ChatWebSocketController(ChatService chatService,
                                   SimpMessagingTemplate messagingTemplate,
                                   ChatRoomMemberRepository chatRoomMemberRepository) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
    }

    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId,
                            @Payload Map<String, String> payload,
                            Principal principal) {
        if (principal == null) return;

        Long senderId = Long.parseLong(principal.getName());
        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) return;

        // Lưu vào DB thông qua ChatService đã có.
        // ChatService.sendMessage() sẽ ném RuntimeException nếu Host bị khóa,
        // phòng bị đóng, hoặc bất kỳ điều kiện vi phạm nào khác.
        ChatMessageResponse saved;
        try {
            saved = chatService.sendMessage(roomId, senderId, content);
        } catch (RuntimeException e) {
            // Gửi lỗi 403 ngược về đúng sender (không broadcast cho cả phòng)
            messagingTemplate.convertAndSendToUser(
                    senderId.toString(),
                    "/queue/errors",
                    Map.of(
                            "code", 403,
                            "message", e.getMessage(),
                            "roomId", roomId
                    )
            );
            return;
        }

        // Broadcast tới tất cả subscriber của phòng
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, saved);

        // Thông báo danh sách phòng cho từng thành viên
        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(roomId);
        for (ChatRoomMember member : members) {
            messagingTemplate.convertAndSendToUser(
                    member.getUserId().toString(),
                    "/queue/chat-list",
                    Map.of("roomId", roomId)
            );
        }
    }
}
