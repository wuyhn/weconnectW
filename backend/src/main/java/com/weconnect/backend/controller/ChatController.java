package com.weconnect.backend.controller;

import com.weconnect.backend.dto.ChatMessageResponse;
import com.weconnect.backend.dto.ChatRoomResponse;
import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.service.ChatService;
import com.weconnect.backend.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final PostService postService;

    public ChatController(ChatService chatService, PostService postService) {
        this.chatService = chatService;
        this.postService = postService;
    }

    // Danh sách phòng chat
    @GetMapping("/rooms")
    public ResponseEntity<?> getRooms(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<ChatRoomResponse> rooms = chatService.getUserRooms(user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(rooms).build());
    }

    // Danh sach direct room tu nguoi chua la ban be
    @GetMapping("/message-requests")
    public ResponseEntity<?> getMessageRequests(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<ChatRoomResponse> rooms = chatService.getMessageRequests(user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(rooms).build());
    }

    // Chi tiết phòng
    @GetMapping("/rooms/{id}")
    public ResponseEntity<?> getRoom(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            ChatRoomResponse room = chatService.getRoom(id, user.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Thành công").result(room).build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1020).message(e.getMessage()).build());
        }
    }

    // Tạo phòng nhóm bạn bè
    @PostMapping("/rooms")
    public ResponseEntity<?> createGroupRoom(Authentication authentication,
                                             @RequestBody Map<String, Object> body) {
        User user = (User) authentication.getPrincipal();
        String title = (String) body.get("title");
        @SuppressWarnings("unchecked")
        List<Integer> memberIdsRaw = (List<Integer>) body.get("memberIds");
        List<Long> memberIds = memberIdsRaw.stream().map(Integer::longValue).toList();

        ChatRoomResponse room = chatService.createGroupRoom(user.getId(), title, memberIds);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Tạo phòng thành công!").result(room).build());
    }

    // Lấy hoặc tạo phòng DM
    @GetMapping("/direct/{userId}")
    public ResponseEntity<?> getDirectRoom(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            ChatRoomResponse room = chatService.getOrCreateDirectRoom(user.getId(), userId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Thành công").result(room).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1020).message(e.getMessage()).build());
        }
    }

    // Lịch sử tin nhắn
    @GetMapping("/rooms/{id}/messages")
    public ResponseEntity<?> getMessages(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<ChatMessageResponse> messages = chatService.getMessages(id, user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(messages).build());
    }

    // Polling tin nhắn mới
    @GetMapping("/rooms/{id}/messages/new")
    public ResponseEntity<?> getNewMessages(@PathVariable Long id,
                                            @RequestParam Long afterId,
                                            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<ChatMessageResponse> messages = chatService.getNewMessages(id, afterId, user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(messages).build());
    }

    // Gửi tin nhắn
    @PostMapping("/rooms/{id}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable Long id,
                                         Authentication authentication,
                                         @RequestBody Map<String, String> body) {
        User user = (User) authentication.getPrincipal();
        String content = body.get("content");
        try {
            ChatMessageResponse message = chatService.sendMessage(id, user.getId(), content);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã gửi").result(message).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1021).message(e.getMessage()).build());
        }
    }

    // Dọn dẹp các phòng chat activity không hợp lệ (postId null hoặc post đã bị xóa)
    @DeleteMapping("/rooms/cleanup")
    public ResponseEntity<?> cleanupInvalidRooms() {
        int deleted = chatService.cleanupInvalidRooms();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã xóa " + deleted + " phòng chat không hợp lệ.").build());
    }

    // Lấy phòng chat hoạt động theo postId
    @GetMapping("/rooms/post/{postId}")
    public ResponseEntity<?> getRoomByPost(@PathVariable Long postId) {
        try {
            ChatRoomResponse room = chatService.getRoomByPostId(postId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Thành công").result(room).build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1020).message(e.getMessage()).build());
        }
    }

    // Đánh dấu đã đọc tất cả tin nhắn trong phòng
    @PutMapping("/rooms/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        chatService.markAsRead(id, user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã đánh dấu đã đọc.").build());
    }

    // Xóa thành viên khỏi nhóm (chỉ owner)
    @DeleteMapping("/rooms/{id}/members/{memberId}")
    public ResponseEntity<?> removeMember(@PathVariable Long id,
                                          @PathVariable Long memberId,
                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            chatService.removeMember(id, user.getId(), memberId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã xóa thành viên.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ApiResponse.builder()
                    .code(1021).message(e.getMessage()).build());
        }
    }

    // Rời nhóm (non-owner)
    @PostMapping("/rooms/{id}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            chatService.leaveRoom(id, user.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã rời nhóm.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1021).message(e.getMessage()).build());
        }
    }

    // Hủy hoạt động qua roomId (chủ phòng activity room)
    @PostMapping("/rooms/{id}/cancel")
    public ResponseEntity<?> cancelActivityRoom(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            Long postId = chatService.getPostIdFromActivityRoom(id, user.getId());
            postService.cancelActivity(postId, user.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã hủy hoạt động.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1005).message(e.getMessage()).build());
        }
    }

    // Xóa phòng chat (chỉ owner)
    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            chatService.deleteRoom(id, user.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã xóa phòng chat.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ApiResponse.builder()
                    .code(1021).message(e.getMessage()).build());
        }
    }
}
