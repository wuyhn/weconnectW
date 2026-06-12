package com.weconnect.backend.controller;

import com.weconnect.backend.dto.PostResponse;
import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.PostMember;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import com.weconnect.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;
    private final UserRepository userRepository;

    public AdminPostController(PostRepository postRepository, PostMemberRepository postMemberRepository, UserRepository userRepository) {
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
        this.userRepository = userRepository;
    }

    // Lấy tất cả posts (cho admin web)
    @GetMapping
    public ResponseEntity<?> getAllPosts() {
        List<Post> posts = postRepository.findAll();
        posts.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        List<PostResponse> responses = posts.stream()
                .map(this::toPostResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(responses).build());
    }

    // Lấy chi tiết 1 post
    @GetMapping("/{id}")
    public ResponseEntity<?> getPost(@PathVariable Long id) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy bài đăng").build());
        }
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(toPostResponse(postOpt.get())).build());
    }

    // Archive post
    @PutMapping("/{id}/archive")
    public ResponseEntity<?> archivePost(@PathVariable Long id) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy bài đăng").build());
        }
        Post post = postOpt.get();
        post.setArchived(true);
        postRepository.save(post);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã lưu trữ bài đăng")
                .result(toPostResponse(post)).build());
    }

    // Unarchive post
    @PutMapping("/{id}/unarchive")
    public ResponseEntity<?> unarchivePost(@PathVariable Long id) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy bài đăng").build());
        }
        Post post = postOpt.get();
        post.setArchived(false);
        postRepository.save(post);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã khôi phục bài đăng")
                .result(toPostResponse(post)).build());
    }

    // Xóa post (admin — không check author)
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy bài đăng").build());
        }
        postMemberRepository.deleteByPostId(id);
        postRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã xóa bài đăng").build());
    }

    private PostResponse toPostResponse(Post post) {
        int memberCount = postMemberRepository.countByPostIdAndStatus(post.getId(), PostMember.Status.APPROVED) + 1;

        String authorName = null;
        String authorAvatarUrl = null;
        User author = userRepository.findById(post.getAuthorId()).orElse(null);
        if (author != null) {
            authorName = author.getFullName();
            authorAvatarUrl = author.getAvatarUrl();
        }

        return PostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .authorName(authorName)
                .authorAvatarUrl(authorAvatarUrl)
                .content(post.getContent())
                .interestTag(post.getInterestTag())
                .location(post.getLocation())
                .imageUrl(post.getImageUrl())
                .maxMembers(post.getMaxMembers())
                .memberCount(memberCount)
                .archived(post.isArchived())
                .cancelled(post.isCancelled())
                .expired(post.isExpired())
                .expirationHours(post.getExpirationHours())
                .startTime(post.getStartTime())
                .endTime(post.getEndTime())
                .activityEndTime(post.getActivityEndTime())
                .activityTimeType(post.getActivityTimeType() != null ? post.getActivityTimeType().name() : null)
                .createdAt(post.getCreatedAt())
                .build();
    }
}
