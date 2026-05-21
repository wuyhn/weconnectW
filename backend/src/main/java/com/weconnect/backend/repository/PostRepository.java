package com.weconnect.backend.repository;

import com.weconnect.backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // Bài đăng active (chưa archived, chưa hết hạn)
    List<Post> findByArchivedFalseAndEndTimeAfterOrderByCreatedAtDesc(LocalDateTime now);

    // Bài đăng của user (active)
    List<Post> findByAuthorIdAndArchivedFalseAndEndTimeAfterOrderByCreatedAtDesc(Long authorId, LocalDateTime now);

    // Bài đăng đã archived hoặc hết hạn của user
    List<Post> findByAuthorIdAndArchivedTrueOrAuthorIdAndEndTimeBeforeOrderByCreatedAtDesc(
            Long authorId1, Long authorId2, LocalDateTime now);

    // Tìm kiếm theo content hoặc interestTag
    List<Post> findByContentContainingIgnoreCaseOrInterestTagContainingIgnoreCase(String content, String tag);

    // Bài đăng của user
    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    // Tất cả bài đăng của user (dùng cho cascade delete)
    List<Post> findByAuthorId(Long authorId);

    // Bài đăng hết hạn chưa gửi thông báo (cho scheduler)
    List<Post> findByArchivedFalseAndExpirationNotifiedFalseAndEndTimeBefore(LocalDateTime now);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
