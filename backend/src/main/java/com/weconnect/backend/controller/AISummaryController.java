package com.weconnect.backend.controller;

import com.weconnect.backend.dto.ChatMessageResponse;
import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.ChatMessage;
import com.weconnect.backend.entity.ChatRoom;
import com.weconnect.backend.entity.ChatRoomMember;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.ChatMessageRepository;
import com.weconnect.backend.repository.ChatRoomMemberRepository;
import com.weconnect.backend.repository.ChatRoomRepository;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.service.AISummaryService;
import com.weconnect.backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class AISummaryController {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final AISummaryService aiSummaryService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public AISummaryController(ChatRoomRepository chatRoomRepository,
                               ChatRoomMemberRepository chatRoomMemberRepository,
                               ChatMessageRepository chatMessageRepository,
                               UserRepository userRepository,
                               AISummaryService aiSummaryService,
                               NotificationService notificationService,
                               SimpMessagingTemplate messagingTemplate) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.aiSummaryService = aiSummaryService;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/rooms/{roomId}/summary")
    public ResponseEntity<?> requestSummary(@PathVariable Long roomId,
                                            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Long userId = currentUser.getId();

        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1020).message("Không tìm thấy phòng chat.").build());
        }

        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            return ResponseEntity.status(403).body(ApiResponse.builder()
                    .code(1021).message("Bạn không phải thành viên phòng chat này.").build());
        }

        // Lấy 50 tin nhắn thực gần nhất (loại trừ SUMMARY/SYSTEM), sắp xếp cũ → mới
        List<ChatMessage> recent = chatMessageRepository.findTop50RealMessagesByRoomId(roomId);
        List<ChatMessage> ordered = new ArrayList<>(recent);
        ordered.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return a.getCreatedAt().compareTo(b.getCreatedAt());
        });

        String summaryText;
        try {
            summaryText = aiSummaryService.summarize(ordered);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1022).message(e.getMessage()).build());
        }

        // Lưu summary vào DB như tin nhắn SUMMARY
        ChatMessage summaryMsg = ChatMessage.builder()
                .roomId(roomId)
                .senderId(0L)
                .content("🤖 AI tóm tắt:\n" + summaryText)
                .type("SUMMARY")
                .build();
        ChatMessage saved = chatMessageRepository.save(summaryMsg);

        // Build response DTO
        String timeStr = saved.getCreatedAt() != null
                ? saved.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")) : "";
        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(saved.getId())
                .roomId(saved.getRoomId())
                .senderId(0L)
                .senderName("")
                .content(saved.getContent())
                .type("SUMMARY")
                .sentByCurrentUser(false)
                .createdAt(saved.getCreatedAt())
                .build();

        // Broadcast qua WebSocket vào phòng chat
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);

        // Gửi notification đến tất cả thành viên
        String roomTitle = room.getTitle() != null ? room.getTitle() : "nhóm";
        String notifMsg = "AI đã tóm tắt cuộc trò chuyện trong " + roomTitle;
        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoomId(roomId);
        for (ChatRoomMember member : members) {
            try {
                notificationService.createChatSummaryNotification(member.getUserId(), notifMsg, roomId);
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã tạo tóm tắt.").result(response).build());
    }
}
