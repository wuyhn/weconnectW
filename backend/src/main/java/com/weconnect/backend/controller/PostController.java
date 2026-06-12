package com.weconnect.backend.controller;

import com.weconnect.backend.dto.PostRequest;
import com.weconnect.backend.dto.PostResponse;
import com.weconnect.backend.dto.JoinGroupResponse;
import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private static final Logger log = LoggerFactory.getLogger(PostController.class);

    private final PostService postService;
    private final SimpMessagingTemplate messagingTemplate;

    public PostController(PostService postService, SimpMessagingTemplate messagingTemplate) {
        this.postService = postService;
        this.messagingTemplate = messagingTemplate;
    }

    // Danh sách bài đăng active
    @GetMapping
    public ResponseEntity<?> getActivePosts(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PostResponse> posts = postService.getActivePosts(user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(posts).build());
    }

    // Chi tiết bài đăng
    @GetMapping("/{id}")
    public ResponseEntity<?> getPost(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            PostResponse post = postService.getPost(id, user.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Thành công").result(post).build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message(e.getMessage()).build());
        }
    }

    // Tạo bài đăng
    @PostMapping
    public ResponseEntity<?> createPost(Authentication authentication,
                                        @RequestBody PostRequest request) {
        User user = (User) authentication.getPrincipal();
        PostResponse post = postService.createPost(user.getId(), request);
        // Broadcast tới tất cả client — bỏ joined/pendingApproval vì đây là state per-user
        PostResponse broadcast = post.toBuilder().joined(false).pendingApproval(false).build();
        messagingTemplate.convertAndSend("/topic/feed", broadcast);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Tạo bài đăng thành công!").result(post).build());
    }

    // Sửa bài đăng
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePost(@PathVariable Long id,
                                        Authentication authentication,
                                        @RequestBody PostRequest request) {
        User user = (User) authentication.getPrincipal();
        try {
            PostResponse post = postService.updatePost(id, user.getId(), request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Cập nhật bài đăng thành công!").result(post).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1005).message(e.getMessage()).build());
        }
    }

    // Hủy hoạt động (chủ bài đăng hủy toàn bộ hoạt động + group chat)
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelActivity(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            postService.cancelActivity(id, user.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã hủy hoạt động.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1005).message(e.getMessage()).build());
        }
    }

    // Xóa bài đăng
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            postService.deletePost(id, user.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Đã xóa bài đăng.").build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1005).message(e.getMessage()).build());
        }
    }

    // Xin tham gia
    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinPost(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            String joinReason = body != null && body.get("joinReason") != null
                    ? body.get("joinReason").toString() : null;
            String requesterProvince = body != null && body.get("requesterProvince") != null
                    ? body.get("requesterProvince").toString() : null;
            String activityProvince = body != null && body.get("activityProvince") != null
                    ? body.get("activityProvince").toString() : null;
            Boolean isFarLocation = body != null && body.get("isFarLocation") != null
                    ? Boolean.parseBoolean(body.get("isFarLocation").toString()) : null;
            JoinGroupResponse result = postService.joinPost(
                    id, user.getId(), joinReason, requesterProvince, activityProvince, isFarLocation);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(result.getMessage()).result(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1006).message(e.getMessage()).build());
        }
    }

    // Duyệt thành viên
    @PostMapping("/{id}/approve/{userId}")
    public ResponseEntity<?> approveMember(@PathVariable Long id,
                                           @PathVariable Long userId,
                                           Authentication authentication) {
        User owner = (User) authentication.getPrincipal();
        try {
            String result = postService.approveMember(id, userId, owner.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1007).message(e.getMessage()).build());
        }
    }

    // Từ chối thành viên
    @PostMapping("/{id}/reject/{userId}")
    public ResponseEntity<?> rejectMember(@PathVariable Long id,
                                          @PathVariable Long userId,
                                          Authentication authentication) {
        User owner = (User) authentication.getPrincipal();
        try {
            String result = postService.rejectMember(id, userId, owner.getId());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message(result).build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1007).message(e.getMessage()).build());
        }
    }

    // Danh sách thành viên
    @GetMapping("/{id}/members")
    public ResponseEntity<?> getMembers(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(postService.getMembers(id)).build());
    }

    // Danh sách chờ duyệt
    @GetMapping("/{id}/pending")
    public ResponseEntity<?> getPendingMembers(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(postService.getPendingMembers(id)).build());
    }

    // Bài đăng của user
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserPosts(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PostResponse> posts = postService.getUserActivePosts(userId, user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(posts).build());
    }

    // Bài đăng đã lưu trữ
    @GetMapping("/user/{userId}/archived")
    public ResponseEntity<?> getUserArchivedPosts(@PathVariable Long userId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PostResponse> posts = postService.getUserArchivedPosts(userId, user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(posts).build());
    }

    // Tìm kiếm bài đăng hoạt động theo keyword.
    // Hỗ trợ cả "keyword" mới và "q" cũ để không phá các client chưa cập nhật.
    @GetMapping("/search")
    public ResponseEntity<?> searchPosts(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String q,
                                         Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String searchKeyword = keyword != null ? keyword : q;
        List<PostResponse> posts = postService.searchPosts(searchKeyword, user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(posts).build());
    }

    // Hoạt động của tôi (bài user đã được duyệt tham gia)
    @GetMapping("/my-activities")
    public ResponseEntity<?> getMyActivities(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PostResponse> posts = postService.getMyActivities(user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(posts).build());
    }

    // Hoạt động của user khác (bài user đã được duyệt tham gia)
    @GetMapping("/user/{userId}/activities")
    public ResponseEntity<?> getUserActivities(@PathVariable Long userId, Authentication authentication) {
        User viewer = (User) authentication.getPrincipal();
        // Lấy activities của target user, nhưng resolve CTA theo viewer
        List<PostResponse> posts = postService.getUserActivities(userId, viewer.getId());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công").result(posts).build());
    }
}
