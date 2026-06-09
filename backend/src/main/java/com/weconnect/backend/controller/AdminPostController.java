package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.Post;
import com.weconnect.backend.entity.PostMember;
import com.weconnect.backend.repository.PostMemberRepository;
import com.weconnect.backend.repository.PostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final PostRepository postRepository;
    private final PostMemberRepository postMemberRepository;

    public AdminPostController(PostRepository postRepository, PostMemberRepository postMemberRepository) {
        this.postRepository = postRepository;
        this.postMemberRepository = postMemberRepository;
    }

    // Lấy tất cả posts (cho admin web)
    @GetMapping
    public ResponseEntity<?> getAllPosts() {
        List<Post> posts = postRepository.findAll();
        posts.forEach(this::attachMemberCount);
        // Sort by createdAt descending
        posts.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(posts).build());
    }

    // Lấy chi tiết 1 post
    @GetMapping("/{id}")
    public ResponseEntity<?> getPost(@PathVariable Long id) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1003).message("Không tìm thấy bài đăng").build());
        }
        attachMemberCount(postOpt.get());
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Thành công")
                .result(postOpt.get()).build());
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
        attachMemberCount(post);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã lưu trữ bài đăng")
                .result(post).build());
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
        attachMemberCount(post);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã khôi phục bài đăng")
                .result(post).build());
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
        // Xóa members trước, sau đó xóa post
        postMemberRepository.deleteByPostId(id);
        postRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000).message("Đã xóa bài đăng").build());
    }

    private void attachMemberCount(Post post) {
        int approvedMembers = postMemberRepository.countByPostIdAndStatus(post.getId(), PostMember.Status.APPROVED);
        post.setMemberCount(approvedMembers + 1);
    }
}
