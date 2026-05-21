package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.service.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    // Gửi lời mời kết bạn
    @PostMapping("/request/{userId}")
    public ResponseEntity<?> sendFriendRequest(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            String result = friendService.sendFriendRequest(user.getId(), userId);
            return ResponseEntity.ok(ApiResponse.builder().code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().code(1010).message(e.getMessage()).build());
        }
    }

    // Chấp nhận lời mời
    @PostMapping("/accept/{userId}")
    public ResponseEntity<?> acceptFriend(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            String result = friendService.acceptFriendRequest(user.getId(), userId);
            return ResponseEntity.ok(ApiResponse.builder().code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().code(1010).message(e.getMessage()).build());
        }
    }

    // Từ chối lời mời
    @PostMapping("/decline/{userId}")
    public ResponseEntity<?> declineFriend(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            String result = friendService.declineFriendRequest(user.getId(), userId);
            return ResponseEntity.ok(ApiResponse.builder().code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().code(1010).message(e.getMessage()).build());
        }
    }

    // Hủy lời mời đã gửi
    @PostMapping("/cancel/{userId}")
    public ResponseEntity<?> cancelFriend(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            String result = friendService.cancelFriendRequest(user.getId(), userId);
            return ResponseEntity.ok(ApiResponse.builder().code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().code(1010).message(e.getMessage()).build());
        }
    }

    // Hủy kết bạn
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> unfriend(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            String result = friendService.unfriend(user.getId(), userId);
            return ResponseEntity.ok(ApiResponse.builder().code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().code(1010).message(e.getMessage()).build());
        }
    }

    // Danh sách bạn bè
    @GetMapping
    public ResponseEntity<?> getFriends(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(friendService.getFriendList(user.getId())).build());
    }

    // Trạng thái quan hệ
    @GetMapping("/status/{userId}")
    public ResponseEntity<?> getFriendStatus(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String status = friendService.getFriendStatus(user.getId(), userId);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(status).build());
    }

    // Trạng thái block 2 chiều dùng cho quyền nhắn tin direct
    @GetMapping("/block-status/{userId}")
    public ResponseEntity<?> getBlockStatus(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Thành công")
                    .result(friendService.getBlockStatus(user.getId(), userId)).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder().code(1012).message(e.getMessage()).build());
        }
    }

    // Số bạn bè
    @GetMapping("/count")
    public ResponseEntity<?> getFriendCount(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(friendService.getFriendCount(user.getId())).build());
    }

    // Chặn user
    @PostMapping("/block/{userId}")
    public ResponseEntity<?> blockUser(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            String result = friendService.blockUser(user.getId(), userId);
            return ResponseEntity.ok(ApiResponse.builder().code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().code(1011).message(e.getMessage()).build());
        }
    }

    // Bỏ chặn
    @DeleteMapping("/block/{userId}")
    public ResponseEntity<?> unblockUser(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            String result = friendService.unblockUser(user.getId(), userId);
            return ResponseEntity.ok(ApiResponse.builder().code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder().code(1011).message(e.getMessage()).build());
        }
    }

    // Danh sách đã chặn
    @GetMapping("/blocked")
    public ResponseEntity<?> getBlockedUsers(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(friendService.getBlockedUsers(user.getId())).build());
    }
}
