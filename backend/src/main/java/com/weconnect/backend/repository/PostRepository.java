package com.weconnect.backend.repository;

import com.weconnect.backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Tìm kiếm bài viết hoạt động theo nội dung chính của bài viết.
    // Lưu ý: schema hiện tại chưa có title/description riêng; content đang là text hiển thị chính.
    @Query("""
            SELECT p FROM Post p
            WHERE p.archived = false
              AND p.cancelled = false
              AND (p.endTime IS NULL OR p.endTime > :now)
              AND LOWER(COALESCE(p.content, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY p.createdAt DESC
            """)
    List<Post> searchPosts(@Param("keyword") String keyword, @Param("now") LocalDateTime now);

    // Bài đăng của user
    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    // Tất cả bài đăng của user (dùng cho cascade delete)
    List<Post> findByAuthorId(Long authorId);

    // Bài đăng hết hạn chưa gửi thông báo (cho scheduler)
    List<Post> findByArchivedFalseAndExpirationNotifiedFalseAndEndTimeBefore(LocalDateTime now);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
