package com.weconnect.backend.controller;

import com.weconnect.backend.dto.ChangePasswordRequest;
import com.weconnect.backend.dto.UpdateProfileRequest;
import com.weconnect.backend.dto.UserProfileResponse;
import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.UserRepository;
import com.weconnect.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public UserController(UserService userService, UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Lấy profile của chính mình
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        UserProfileResponse profile = userService.getProfile(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000)
                .message("Thành công")
                .result(profile)
                .build());
    }

    // Lấy profile của user khác
    @GetMapping("/{id}")
    public ResponseEntity<?> getProfile(@PathVariable Long id, Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            UserProfileResponse profile = userService.getProfile(id, currentUser.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message("Thành công")
                    .result(profile)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003)
                    .message(e.getMessage())
                    .build());
        }
    }

    // Cập nhật profile
    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(Authentication authentication,
                                           @RequestBody UpdateProfileRequest request) {
        User currentUser = (User) authentication.getPrincipal();
        UserProfileResponse profile = userService.updateProfile(currentUser.getId(), request);

        // Broadcast avatar change so all connected clients update in realtime
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
            Map<String, Object> event = new HashMap<>();
            event.put("userId", currentUser.getId());
            event.put("avatarUrl", request.getAvatarUrl());
            messagingTemplate.convertAndSend("/topic/user-updates", (Object) event);
        }

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000)
                .message("Cập nhật thành công!")
                .result(profile)
                .build());
    }

    // Lưu sở thích từ onboarding
    @PutMapping("/me/interests")
    public ResponseEntity<?> saveInterests(Authentication authentication,
                                           @RequestBody Map<String, List<String>> body) {
        User currentUser = (User) authentication.getPrincipal();
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        List<String> interests = body.get("interests");
        if (interests != null) {
            user.setInterestTags(String.join(",", interests));
            userRepository.save(user);
        }

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000)
                .message("Đã lưu sở thích!")
                .result(interests)
                .build());
    }

    // Lấy sở thích của user
    @GetMapping("/me/interests")
    public ResponseEntity<?> getInterests(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        String tags = user.getInterestTags();
        List<String> interests = (tags != null && !tags.isEmpty())
                ? List.of(tags.split(",")) : List.of();

        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000)
                .message("Thành công")
                .result(interests)
                .build());
    }

    // Đổi mật khẩu
    @PutMapping("/me/password")
    public ResponseEntity<?> changePassword(Authentication authentication,
                                            @RequestBody ChangePasswordRequest request) {
        User currentUser = (User) authentication.getPrincipal();
        try {
            String result = userService.changePassword(currentUser.getId(), request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message(result)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1004)
                    .message(e.getMessage())
                    .build());
        }
    }

    // Gợi ý user có cùng sở thích
    @GetMapping("/suggestions")
    public ResponseEntity<?> getSuggestions(Authentication authentication,
                                            @RequestParam(required = false) Long excludeId) {
        User currentUser = (User) authentication.getPrincipal();
        List<Map<String, Object>> suggestions = userService.getSuggestedUsers(
                currentUser.getId(), excludeId);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000)
                .message("Thành công")
                .result(suggestions)
                .build());
    }

    // Xóa tài khoản — xóa ngay, không cần admin duyệt
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteAccount(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        try {
            userService.deleteUser(currentUser.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message("Đã xóa tài khoản thành công!")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1005)
                    .message(e.getMessage())
                    .build());
        }
    }

    // Đăng ký / cập nhật FCM token cho push notification
    @PutMapping("/me/fcm-token")
    public ResponseEntity<?> updateFcmToken(Authentication authentication,
                                            @RequestBody Map<String, String> body) {
        User currentUser = (User) authentication.getPrincipal();
        String fcmToken = body.get("fcmToken");
        userService.updateFcmToken(currentUser.getId(), fcmToken);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000)
                .message("FCM token updated")
                .build());
    }

    // Tìm user theo tên exact (dùng để resolve user_id từ username)
    @GetMapping("/search")
    public ResponseEntity<?> searchByName(@RequestParam String name, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        var user = userRepository.findByFullName(name).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy người dùng").build());
        }
        // Nếu user này đã chặn tôi → hiển thị như không tồn tại
        if (userService.isBlockedBy(user.getId(), currentUser.getId())) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy người dùng").build());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("fullName", user.getFullName());
        userService.appendBlockStatus(result, currentUser.getId(), user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(result)
                .build());
    }

    // Tìm kiếm user theo tên (partial match)
    @GetMapping("/search/partial")
    public ResponseEntity<?> searchUsersPartial(@RequestParam String q,
                                                  Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        List<User> users = userRepository.findByFullNameContainingIgnoreCase(q);
        List<Map<String, Object>> result = users.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> !userService.isBlockedBy(u.getId(), currentUser.getId()))
                .limit(10)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("fullName", u.getFullName());
                    m.put("avatarUrl", u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
                    userService.appendBlockStatus(m, currentUser.getId(), u.getId());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(result).build());
    }
}
