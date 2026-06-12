package com.weconnect.backend.controller;

import com.weconnect.backend.dto.ChatMessageResponse;
import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.AISummary;
import com.weconnect.backend.entity.ChatMessage;
import com.weconnect.backend.entity.ChatRoom;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.AISummaryRepository;
import com.weconnect.backend.repository.ChatMessageRepository;
import com.weconnect.backend.repository.ChatRoomMemberRepository;
import com.weconnect.backend.repository.ChatRoomRepository;
import com.weconnect.backend.service.AISummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class AISummaryController {

    // Per-user cooldown: tránh spam (30 giây)
    private static final ConcurrentHashMap<Long, LocalDateTime> lastUserSummaryTime = new ConcurrentHashMap<>();
    private static final int USER_COOLDOWN_SECONDS = 30;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AISummaryRepository aiSummaryRepository;
    private final AISummaryService aiSummaryService;
    private final SimpMessagingTemplate messagingTemplate;

    public AISummaryController(ChatRoomRepository chatRoomRepository,
                               ChatRoomMemberRepository chatRoomMemberRepository,
                               ChatMessageRepository chatMessageRepository,
                               AISummaryRepository aiSummaryRepository,
                               AISummaryService aiSummaryService,
                               SimpMessagingTemplate messagingTemplate) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.aiSummaryRepository = aiSummaryRepository;
        this.aiSummaryService = aiSummaryService;
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
                    .code(1020).message("Khong tim thay phong chat.").build());
        }

        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            return ResponseEntity.status(403).body(ApiResponse.builder()
                    .code(1021).message("Ban khong phai thanh vien phong chat nay.").build());
        }

        // Per-user cooldown: tránh spam Gemini API
        LocalDateTime lastUser = lastUserSummaryTime.get(userId);
        if (lastUser != null && lastUser.isAfter(LocalDateTime.now().minusSeconds(USER_COOLDOWN_SECONDS))) {
            return ResponseEntity.status(429).body(ApiResponse.builder()
                    .code(1023).message("Vui long cho " + USER_COOLDOWN_SECONDS + " giay truoc khi tom tat lai.")
                    .build());
        }

        // Lấy 50 tin nhắn thực gần nhất để gửi cho Gemini
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

        lastUserSummaryTime.put(userId, LocalDateTime.now());

        // Lưu vĩnh viễn vào bảng ai_summaries — chỉ gắn với userId này
        AISummary saved = aiSummaryRepository.save(AISummary.builder()
                .roomId(roomId)
                .userId(userId)
                .content(summaryText)
                .build());

        // Gửi riêng cho user đã yêu cầu qua user-specific queue (không broadcast)
        ChatMessageResponse summaryResponse = ChatMessageResponse.builder()
                .id(saved.getId())
                .roomId(roomId)
                .senderId(0L)
                .senderName("AI")
                .content(summaryText)
                .type("SUMMARY")
                .sentByCurrentUser(false)
                .createdAt(saved.getCreatedAt())
                .build();

        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/ai-summary",
                summaryResponse
        );

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Tom tat thanh cong.").build());
    }
}
